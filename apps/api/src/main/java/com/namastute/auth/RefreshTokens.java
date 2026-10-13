package com.namastute.auth;

import com.namastute.shared.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

// Long-lived refresh tokens: random strings, stored hashed in the sessions table, rotated on every use
@Service
class RefreshTokens {

    static final Duration LIFETIME = Duration.ofDays(30);

    private final SessionRepository sessions;
    private final SecureRandom random = new SecureRandom();

    RefreshTokens(SessionRepository sessions) {
        this.sessions = sessions;
    }

    record Rotated(UUID userId, String token) {}

    String create(UUID userId) {
        byte[] bytes = new byte[32];   // 256 random bits: far too many possibilities to guess
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.save(new Session(userId, sha256(token), Instant.now().plus(LIFETIME)));
        return token;
    }

    Rotated rotate(String token) {
        Session session = sessions.findByTokenHash(sha256(token)).orElseThrow(RefreshTokens::invalid);
        Instant now = Instant.now();
        // revokeIfActive returns 0 when the token was already used: a copy is being replayed, so log out every device
        if (session.getRevokedAt() != null || sessions.revokeIfActive(session.getId(), now) == 0) {
            sessions.revokeAllForUser(session.getUserId(), now);
            throw invalid();
        }
        if (session.getExpiresAt().isBefore(now)) {
            throw invalid();
        }
        return new Rotated(session.getUserId(), create(session.getUserId()));
    }

    void revoke(String token) {
        sessions.findByTokenHash(sha256(token))
                .ifPresent(s -> sessions.revokeIfActive(s.getId(), Instant.now()));
    }

    // Plain SHA-256 is enough here (unlike the 6-digit OTP): 256 random bits can't be brute-forced
    private static String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);   // every JVM ships it; can't happen
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_INVALID", "Session expired, please log in again");
    }
}
