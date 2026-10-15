package com.namastute.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.namastute.users.UserService;
import com.sun.net.httpserver.HttpServer;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

// Web Push end to end (Learning Notes 58): a fake push service on localhost receives what the API sends, and the test
// decrypts it like a browser would (RFC 8291). The allowlist is widened to plain-HTTP localhost for this test only.
// Terminal 2, eight hours ahead, fresh users every test.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"namastute.push.allowed-hosts=localhost", "namastute.push.require-https=false"})
@AutoConfigureMockMvc
class NotificationsTest {

    static final Instant EXIT = Instant.now().plus(Duration.ofHours(8));

    record Received(String path, Map<String, String> headers, byte[] body) {}

    static HttpServer pushService;
    static final BlockingQueue<Received> received = new LinkedBlockingQueue<>();

    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired SimpUserRegistry sockets;

    // Answers 201 Created like a real push service, or 410 Gone for paths under /gone/
    @BeforeAll
    static void startPushService() throws Exception {
        pushService = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        pushService.createContext("/", exchange -> {
            Map<String, String> headers = new java.util.HashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.get(0)));
            received.add(new Received(exchange.getRequestURI().getPath(), headers, exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(exchange.getRequestURI().getPath().startsWith("/gone/") ? 410 : 201, -1);
            exchange.close();
        });
        pushService.start();
    }

    @AfterAll
    static void stopPushService() {
        pushService.stop(0);
    }

    @Test
    void pushesArriveEncryptedSignedAndMinimal() throws Exception {
        UUID asha = user("Asha Rao"), ravi = user("Ravi Kumar");
        String ashaTrip = trip(asha);
        trip(ravi);
        Browser ashaPhone = new Browser(), raviPhone = new Browser();
        subscribe(asha, ashaPhone).andExpect(status().isNoContent());
        subscribe(ravi, raviPhone).andExpect(status().isNoContent());

        ask(ravi, ashaTrip);
        Received ask = next(ashaPhone);
        assertThat(ask.headers()).containsEntry("content-encoding", "aes128gcm").containsEntry("ttl", "3600");
        // VAPID: a signed JWT plus our public key, which must match what the browser subscribed with
        assertThat(ask.headers().get("authorization")).startsWith("vapid t=").endsWith(", k=" + publicKey());
        String claims = new String(Base64.getUrlDecoder().decode(ask.headers().get("authorization").split("[ .]")[2]));
        assertThat(claims).contains("\"aud\":\"http://localhost:" + pushService.getAddress().getPort() + "\"");
        String message = ashaPhone.decrypt(ask.body());
        assertThat(JsonPath.<Map<String, Object>>read(message, "$")).isEqualTo(
                Map.of("title", "Ravi wants to share a cab", "body", "Open Namastute to answer"));   // a first name, nothing else

        String request = JsonPath.read(mvc.perform(get("/api/v1/requests").with(as(asha))).andReturn().getResponse()
                .getContentAsString(), "$.received[0].id");
        mvc.perform(post("/api/v1/requests/" + request + "/accept").with(as(asha))).andExpect(status().isNoContent());
        assertThat(raviPhone.decrypt(next(raviPhone).body())).contains("You're in a cab group").contains("Asha said yes");
    }

    @Test
    void chatPushesOnlyReachMembersWhoAreNotConnected() throws Exception {
        UUID asha = user("Asha Rao"), keshav = user("Keshav Dixit");
        String group = pair(keshav, asha);
        Browser keshavPhone = new Browser();
        subscribe(keshav, keshavPhone).andExpect(status().isNoContent());
        StompSession keshavLive = connect(keshav);
        StompSession ashaLive = connect(asha);

        ashaLive.send("/app/groups/" + group + "/chat", Map.of("body", "At pillar 12", "clientId", "c-1"));
        assertThat(poll(keshavPhone, 1)).as("Keshav has the app open: the message arrives live, no push").isNull();

        keshavLive.disconnect();
        for (int i = 0; i < 100 && sockets.getUser(keshav.toString()) != null; i++) {
            Thread.sleep(20);
        }
        ashaLive.send("/app/groups/" + group + "/chat", Map.of("body", "At pillar 12", "clientId", "c-2"));
        String push = keshavPhone.decrypt(next(keshavPhone).body());
        assertThat(push).contains("New message in your cab group").doesNotContain("pillar");   // never the text
    }

    @Test
    void deadSubscriptionsAreDeleted() throws Exception {
        UUID asha = user("Asha Rao"), ravi = user("Ravi Kumar");
        String ashaTrip = trip(asha);
        trip(ravi);
        Browser gone = new Browser("/gone/");
        subscribe(asha, gone).andExpect(status().isNoContent());

        ask(ravi, ashaTrip);
        next(gone);   // the push service answers 410 Gone
        for (int i = 0; i < 100 && subscriptionsOf(asha) > 0; i++) {
            Thread.sleep(20);
        }
        assertThat(subscriptionsOf(asha)).isZero();
    }

    @Test
    void endpointsOutsideTheAllowlistAndBadKeysAreRefused() throws Exception {
        UUID asha = user("Asha Rao");
        Browser phone = new Browser();
        // SSRF: the cloud's metadata address must never become a place our server sends requests to
        mvc.perform(post("/api/v1/push/subscriptions").with(as(asha)).contentType(MediaType.APPLICATION_JSON)
                        .content(phone.json().replace("http://localhost:" + pushService.getAddress().getPort(), "http://169.254.169.254")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("BAD_PUSH_ENDPOINT"));
        mvc.perform(post("/api/v1/push/subscriptions").with(as(asha)).contentType(MediaType.APPLICATION_JSON)
                        .content(phone.json().replace(phone.auth, "c2hvcnQ")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("BAD_PUSH_KEYS"));
        assertThat(subscriptionsOf(asha)).isZero();
    }

    @Test
    void theProductionAllowlistOnlyTakesHttpsPushServices() {
        List<String> hosts = List.of("fcm.googleapis.com", "push.services.mozilla.com", "push.apple.com", "notify.windows.com");
        assertThat(PushService.isAllowedEndpoint("https://fcm.googleapis.com/fcm/send/abc", hosts, true)).isTrue();
        assertThat(PushService.isAllowedEndpoint("https://updates.push.services.mozilla.com/wpush/v2/abc", hosts, true)).isTrue();
        assertThat(PushService.isAllowedEndpoint("https://web.push.apple.com/abc", hosts, true)).isTrue();
        assertThat(PushService.isAllowedEndpoint("http://fcm.googleapis.com/fcm/send/abc", hosts, true)).as("not HTTPS").isFalse();
        assertThat(PushService.isAllowedEndpoint("https://fcm.googleapis.com.evil.example/x", hosts, true)).isFalse();
        assertThat(PushService.isAllowedEndpoint("https://evilpush.apple.com/x", hosts, true)).isFalse();
        assertThat(PushService.isAllowedEndpoint("https://fcm.googleapis.com@169.254.169.254/x", hosts, true)).isFalse();
        assertThat(PushService.isAllowedEndpoint("https://169.254.169.254/latest/meta-data", hosts, true)).isFalse();
        assertThat(PushService.isAllowedEndpoint("not a url", hosts, true)).isFalse();
    }

    @Test
    void aSharedBrowserFollowsWhoeverLoggedInLastAndUnsubscribeRemovesIt() throws Exception {
        UUID asha = user("Asha Rao"), ravi = user("Ravi Kumar");
        Browser shared = new Browser();
        subscribe(asha, shared).andExpect(status().isNoContent());
        subscribe(ravi, shared).andExpect(status().isNoContent());   // Asha logged out without unsubscribing; Ravi logs in
        assertThat(subscriptionsOf(asha)).isZero();
        assertThat(subscriptionsOf(ravi)).isOne();

        mvc.perform(delete("/api/v1/push/subscriptions").param("endpoint", shared.endpoint).with(as(asha)))
                .andExpect(status().isNoContent());
        assertThat(subscriptionsOf(ravi)).as("Asha can't remove Ravi's").isOne();
        mvc.perform(delete("/api/v1/push/subscriptions").param("endpoint", shared.endpoint).with(as(ravi)))
                .andExpect(status().isNoContent());
        assertThat(subscriptionsOf(ravi)).isZero();
    }

    // ---- a browser's side of Web Push ----

    // What a browser keeps for one subscription: its own P-256 key pair, a 16-byte auth secret and the endpoint
    class Browser {
        final KeyPair keys;
        final byte[] authSecret = new byte[16];
        final String endpoint, p256dh, auth;

        Browser() throws Exception {
            this("/push/");
        }

        Browser(String pathPrefix) throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            keys = generator.generateKeyPair();
            new java.security.SecureRandom().nextBytes(authSecret);
            endpoint = "http://localhost:" + pushService.getAddress().getPort() + pathPrefix + UUID.randomUUID();
            p256dh = Base64.getUrlEncoder().withoutPadding().encodeToString(uncompressed((ECPublicKey) keys.getPublic()));
            auth = Base64.getUrlEncoder().withoutPadding().encodeToString(authSecret);
        }

        String json() {
            return "{\"endpoint\":\"%s\",\"keys\":{\"p256dh\":\"%s\",\"auth\":\"%s\"}}".formatted(endpoint, p256dh, auth);
        }

        // RFC 8291 + RFC 8188: header (salt, record size, sender's public key), then one AES-128-GCM record
        String decrypt(byte[] body) throws Exception {
            ByteBuffer in = ByteBuffer.wrap(body);
            byte[] salt = new byte[16];
            in.get(salt);
            in.getInt();   // record size
            byte[] senderKey = new byte[in.get()];
            in.get(senderKey);
            byte[] ciphertext = new byte[in.remaining()];
            in.get(ciphertext);

            KeyAgreement ecdh = KeyAgreement.getInstance("ECDH");
            ecdh.init(keys.getPrivate());
            ecdh.doPhase(publicKeyFrom(senderKey), true);
            byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII),
                    uncompressed((ECPublicKey) keys.getPublic()), senderKey);
            byte[] ikm = hkdf(authSecret, ecdh.generateSecret(), keyInfo, 32);
            byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
            byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] padded = aes.doFinal(ciphertext);
            int end = padded.length - 1;
            while (padded[end] == 0) {
                end--;
            }
            assertThat(padded[end]).as("last-record delimiter").isEqualTo((byte) 2);
            return new String(padded, 0, end, StandardCharsets.UTF_8);
        }

        private ECPublicKey publicKeyFrom(byte[] uncompressed) throws Exception {
            ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
                    new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
            return (ECPublicKey) KeyFactory.getInstance("EC")
                    .generatePublic(new ECPublicKeySpec(point, ((ECPublicKey) keys.getPublic()).getParams()));
        }
    }

    // HKDF (RFC 5869) with a single block of output, which is all Web Push needs
    private static byte[] hkdf(byte[] salt, byte[] secret, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(secret);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        return Arrays.copyOf(mac.doFinal(concat(info, new byte[] {1})), length);
    }

    private static byte[] uncompressed(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 4;
        copyFixed(key.getW().getAffineX(), out, 1);
        copyFixed(key.getW().getAffineY(), out, 33);
        return out;
    }

    // A coordinate as exactly 32 bytes (BigInteger may add a sign byte or drop leading zeros)
    private static void copyFixed(BigInteger n, byte[] out, int at) {
        byte[] b = n.toByteArray();
        int len = Math.min(b.length, 32);
        System.arraycopy(b, b.length - len, out, at + 32 - len, len);
    }

    private static byte[] concat(byte[]... parts) {
        ByteBuffer out = ByteBuffer.allocate(Arrays.stream(parts).mapToInt(p -> p.length).sum());
        Arrays.stream(parts).forEach(out::put);
        return out.array();
    }

    // ---- helpers ----

    private Received next(Browser browser) throws Exception {
        Received r = poll(browser, 5);
        assertThat(r).as("a push for " + browser.endpoint).isNotNull();
        return r;
    }

    // Other tests' pushes land in the same queue, so look for this browser's endpoint
    private Received poll(Browser browser, int seconds) throws Exception {
        String path = java.net.URI.create(browser.endpoint).getPath();
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < until) {
            Received r = received.poll(50, TimeUnit.MILLISECONDS);
            if (r != null && r.path().equals(path)) {
                return r;
            }
        }
        return null;
    }

    private ResultActions subscribe(UUID user, Browser browser) throws Exception {
        return mvc.perform(post("/api/v1/push/subscriptions").with(as(user)).contentType(MediaType.APPLICATION_JSON).content(browser.json()));
    }

    private String publicKey() throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/push/public-key").with(as(UUID.randomUUID())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.publicKey");
    }

    private int subscriptionsOf(UUID user) {
        return jdbc.queryForObject("select count(*) from push_subscriptions where user_id = ?", Integer.class, user);
    }

    private void ask(UUID from, String toTripId) throws Exception {
        mvc.perform(post("/api/v1/requests").with(as(from)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"toTripId\":\"" + toTripId + "\"}")).andExpect(status().isCreated());
    }

    // Keshav asks Asha, Asha accepts; returns the group id
    private String pair(UUID keshav, UUID asha) throws Exception {
        trip(keshav);
        ask(keshav, trip(asha));
        String request = JsonPath.read(mvc.perform(get("/api/v1/requests").with(as(asha))).andReturn().getResponse()
                .getContentAsString(), "$.received[0].id");
        mvc.perform(post("/api/v1/requests/" + request + "/accept").with(as(asha))).andExpect(status().isNoContent());
        return JsonPath.read(mvc.perform(get("/api/v1/groups/current").with(as(asha))).andReturn().getResponse()
                .getContentAsString(), "$.id");
    }

    private String trip(UUID user) throws Exception {
        String json = """
                {"terminalId":"DEL-T2","exitAt":"%s","destLat":28.4936,"destLng":77.0930,"destArea":"DLF Phase 3, Gurugram",\
                "seats":1,"bags":1,"womenOnly":false}""".formatted(EXIT);
        return JsonPath.read(mvc.perform(post("/api/v1/trips").with(as(user)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private UUID user(String name) {
        UUID id = users.findOrCreateByPhone("+9194" + String.format("%08d", (int) (Math.random() * 100_000_000)));
        jdbc.update("update users set name = ? where id = ?", name, id);
        return id;
    }

    private StompSession connect(UUID user) throws Exception {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("namastute-api").subject(user.toString())
                .issuedAt(now).expiresAt(now.plus(Duration.ofMinutes(15))).build();
        StompHeaders connect = new StompHeaders();
        connect.add("Authorization", "Bearer "
                + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue());
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.setOrigin("http://localhost:3000");
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", handshake, connect, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
        for (int i = 0; i < 100 && sockets.getUser(user.toString()) == null; i++) {
            Thread.sleep(20);   // until the server has registered the connection
        }
        return session;
    }

    private static RequestPostProcessor as(UUID user) {
        return jwt().jwt(j -> j.subject(user.toString()));
    }
}
