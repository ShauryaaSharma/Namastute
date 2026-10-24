package com.namastute.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

// One refresh token (one logged-in device). We store only the token's SHA-256 hash.
@Entity
@Table(name = "sessions")
class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;

    private String tokenHash;

    private Instant expiresAt;

    private Instant revokedAt;

    private UUID parentId;   // the session this one replaced (null for a fresh login)

    protected Session() {
        // required by JPA
    }

    Session(UUID userId, String tokenHash, Instant expiresAt, UUID parentId) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.parentId = parentId;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }
}
