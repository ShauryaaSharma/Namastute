package com.namastute.notifications;

import com.namastute.shared.ApiException;
import com.zerodeplibs.webpush.PushSubscription;
import com.zerodeplibs.webpush.VAPIDKeyPair;
import com.zerodeplibs.webpush.httpclient.StandardHttpClientRequestPreparer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

// Web Push (Learning Notes 58): stores subscriptions and sends encrypted, VAPID-signed messages to push services.
// The crypto is the zerodep-web-push-java library's; nothing here is hand-written cryptography.
@Service
public class PushService {

    // What a notification says. Shown on lock screens, so callers keep it minimal: first names at most (58.5).
    public record Push(String title, String body, Duration ttl) {}

    record Subscription(UUID id, String endpoint, String p256dh, String auth) {}

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final JdbcClient db;
    private final VAPIDKeyPair vapid;
    private final ObjectMapper json;
    private final String contact;
    private final List<String> allowedHosts;
    private final boolean requireHttps;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    PushService(JdbcClient db, VAPIDKeyPair vapid, ObjectMapper json,
                @Value("${namastute.push.contact}") String contact,
                @Value("${namastute.push.allowed-hosts}") List<String> allowedHosts,
                @Value("${namastute.push.require-https}") boolean requireHttps) {
        this.db = db;
        this.vapid = vapid;
        this.json = json;
        this.contact = contact;
        this.allowedHosts = allowedHosts;
        this.requireHttps = requireHttps;
    }

    String publicKey() {
        return vapid.extractPublicKeyInUncompressedFormAsString();
    }

    // One row per browser (endpoint is unique). Re-subscribing from the same browser as someone else moves it to them,
    // so a shared browser never gets the previous person's notifications.
    void subscribe(UUID userId, String endpoint, String p256dh, String auth) {
        if (!isAllowedEndpoint(endpoint, allowedHosts, requireHttps)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_PUSH_ENDPOINT", "That isn't a push service address");
        }
        if (decodedLength(p256dh) != 65 || decodedLength(auth) != 16) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_PUSH_KEYS", "Those push keys aren't valid");
        }
        db.sql("""
                        insert into push_subscriptions (user_id, endpoint, keys) values (?, ?, cast(? as jsonb))
                        on conflict (endpoint) do update set user_id = excluded.user_id, keys = excluded.keys""")
                .params(userId, endpoint, json.writeValueAsString(Map.of("p256dh", p256dh, "auth", auth))).update();
    }

    void unsubscribe(UUID userId, String endpoint) {
        db.sql("delete from push_subscriptions where user_id = ? and endpoint = ?").params(userId, endpoint).update();
    }

    // Sends to every browser these people subscribed. Subscriptions are read now (inside the caller's transaction,
    // same connection); the HTTP calls happen only after the commit, so nobody hears about a change that rolled back,
    // and asynchronously, so a slow push service never slows the request.
    public void send(Collection<UUID> userIds, Push push) {
        if (userIds.isEmpty()) {
            return;
        }
        List<Subscription> subscriptions = db.sql("""
                        select id, endpoint, keys->>'p256dh' as p256dh, keys->>'auth' as auth
                        from push_subscriptions where user_id in (:ids)""")
                .param("ids", userIds).query(Subscription.class).list();
        if (subscriptions.isEmpty()) {
            return;
        }
        byte[] payload = json.writeValueAsBytes(Map.of("title", push.title(), "body", push.body()));
        Runnable deliverAll = () -> subscriptions.forEach(s -> deliver(s, payload, push.ttl()));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliverAll.run();
                }
            });
        } else {
            deliverAll.run();
        }
    }

    private void deliver(Subscription s, byte[] payload, Duration ttl) {
        PushSubscription.Keys keys = new PushSubscription.Keys();
        keys.setP256dh(s.p256dh());
        keys.setAuth(s.auth());
        PushSubscription target = new PushSubscription();
        target.setEndpoint(s.endpoint());
        target.setKeys(keys);
        var request = StandardHttpClientRequestPreparer.getBuilder()
                .pushSubscription(target)
                .vapidJWTExpiresAfter(12, TimeUnit.HOURS)
                .vapidJWTSubject(contact)
                .pushMessage(payload)
                .ttl(ttl.toSeconds(), TimeUnit.SECONDS)
                .urgencyHigh()   // everything we send is time-sensitive at an airport
                .build(vapid)
                .toRequest();
        http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    // 404/410: the browser unsubscribed or the subscription expired; stop pushing into the void (58.6)
                    if (response.statusCode() == 404 || response.statusCode() == 410) {
                        db.sql("delete from push_subscriptions where id = ?").param(s.id()).update();
                    } else if (response.statusCode() >= 300) {
                        log.warn("Push service answered {} for subscription {}", response.statusCode(), s.id());
                    }
                })
                .exceptionally(e -> {
                    log.warn("Push to subscription {} failed: {}", s.id(), e.getMessage());
                    return null;
                });
    }

    // SSRF defence (58.7): only HTTPS addresses on a known push service (or its subdomains)
    static boolean isAllowedEndpoint(String endpoint, List<String> allowedHosts, boolean requireHttps) {
        try {
            URI uri = new URI(endpoint);
            String host = uri.getHost();
            boolean schemeOk = "https".equals(uri.getScheme()) || (!requireHttps && "http".equals(uri.getScheme()));
            return schemeOk && host != null && uri.getUserInfo() == null
                    && allowedHosts.stream().anyMatch(h -> host.equals(h) || host.endsWith("." + h));
        } catch (Exception e) {
            return false;
        }
    }

    private static int decodedLength(String base64Url) {
        try {
            return Base64.getUrlDecoder().decode(base64Url).length;
        } catch (IllegalArgumentException | NullPointerException e) {
            return -1;
        }
    }
}
