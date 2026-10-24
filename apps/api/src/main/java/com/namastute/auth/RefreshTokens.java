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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

// Long-lived refresh tokens: random strings, stored hashed in the sessions table, rotated on every use (Learning Notes 24.4)
@Service
class RefreshTokens {

    static final Duration LIFETIME = Duration.ofDays(30);
    // How long a just-rotated token may be presented again: long enough for a retry, short to limit a thief's chance
    static final Duration GRACE = Duration.ofSeconds(30);

    private final SessionRepository sessions;
    private final SecureRandom random = new SecureRandom();

    RefreshTokens(SessionRepository sessions) {
        this.sessions = sessions;
    }

    record Rotated(UUID userId, String token) {}

    String create(UUID userId) {
        return create(userId, null);
    }

    private String create(UUID userId, UUID parentId) {
        byte[] bytes = new byte[32];   // 256 random bits: far too many possibilities to guess
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.save(new Session(userId, sha256(token), Instant.now().plus(LIFETIME), parentId));
        return token;
    }

    Rotated rotate(String token) {
        Session session = sessions.findByTokenHash(sha256(token)).orElseThrow(RefreshTokens::invalid);
        Instant now = Instant.now();
        // revokeIfActive returns 0 when the token was already used: a lost response being retried, or a stolen copy
        if (session.getRevokedAt() != null || sessions.revokeIfActive(session.getId(), now) == 0) {
            return retryOrLogOutEverywhere(session, now);
        }
        if (session.getExpiresAt().isBefore(now)) {
            throw invalid();
        }
        return new Rotated(session.getUserId(), create(session.getUserId(), session.getId()));
    }

    // The token was used before. Seconds after its rotation, with its replacement still active, it's most likely
    // the same browser whose refresh response got lost: rotate the replacement it never received (Learning Notes 24.7).
    // Anything else (too late, or the replacement already used) is a replay: log out every device (24.4).
    private Rotated retryOrLogOutEverywhere(Session used, Instant now) {
        // null revokedAt here = another request revoked it a moment after we read it, so it was rotated just now
        Instant rotatedAt = Objects.requireNonNullElse(used.getRevokedAt(), now);
        Optional<Session> replacement = sessions.findByParentIdAndRevokedAtIsNull(used.getId());
        if (rotatedAt.isAfter(now.minus(GRACE)) && replacement.isPresent()
                && sessions.revokeIfActive(replacement.get().getId(), now) == 1) {
            // The new session's parent is the used one, so a second lost response inside the window works too
            return new Rotated(used.getUserId(), create(used.getUserId(), used.getId()));
        }
        sessions.revokeAllForUser(used.getUserId(), now);
        throw invalid();
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
