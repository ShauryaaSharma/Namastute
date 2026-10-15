package com.namastute.notifications;

import com.zerodeplibs.webpush.VAPIDKeyPair;
import com.zerodeplibs.webpush.VAPIDKeyPairs;
import com.zerodeplibs.webpush.key.PrivateKeySources;
import com.zerodeplibs.webpush.key.PublicKeySources;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PushController {

    record PublicKey(String publicKey) {}

    // The shape the browser's PushSubscription.toJSON() produces
    record SubscriptionRequest(@NotBlank String endpoint, @NotNull @Valid Keys keys) {
        record Keys(@NotBlank String p256dh, @NotBlank String auth) {}
    }

    private final PushService push;

    PushController(PushService push) {
        this.push = push;
    }

    // Our VAPID public key, base64url: the browser subscribes with it (Learning Notes 58.2)
    @GetMapping("/api/v1/push/public-key")
    PublicKey publicKey() {
        return new PublicKey(push.publicKey());
    }

    @PostMapping("/api/v1/push/subscriptions")
    ResponseEntity<Void> subscribe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SubscriptionRequest req) {
        push.subscribe(userId(jwt), req.endpoint(), req.keys().p256dh(), req.keys().auth());
        return ResponseEntity.noContent().build();
    }

    // On logout: this browser stops getting this person's notifications
    @DeleteMapping("/api/v1/push/subscriptions")
    ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal Jwt jwt, @RequestParam String endpoint) {
        push.unsubscribe(userId(jwt), endpoint);
        return ResponseEntity.noContent().build();
    }

    // VAPID keys from settings: base64url PKCS#8 private key + base64url uncompressed public key (one line each)
    @Bean
    static VAPIDKeyPair vapidKeyPair(@Value("${namastute.push.vapid-private-key}") String privateKey,
                                     @Value("${namastute.push.vapid-public-key}") String publicKey) {
        return VAPIDKeyPairs.of(
                PrivateKeySources.ofPKCS8Bytes(Base64.getUrlDecoder().decode(privateKey)),
                PublicKeySources.ofUncompressedBytes(Base64.getUrlDecoder().decode(publicKey)));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
