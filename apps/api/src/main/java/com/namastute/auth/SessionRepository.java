package com.namastute.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

interface SessionRepository extends JpaRepository<Session, UUID> {

    Optional<Session> findByTokenHash(String tokenHash);

    // The still-active session that replaced this one; rotation keeps at most one per parent (Learning Notes 24.7)
    Optional<Session> findByParentIdAndRevokedAtIsNull(UUID parentId);

    // Revokes one session only if it is still active; returns 1 if this call revoked it, 0 if it was already revoked
    @Modifying
    @Transactional
    @Query("update Session s set s.revokedAt = :now where s.id = :id and s.revokedAt is null")
    int revokeIfActive(UUID id, Instant now);

    @Modifying
    @Transactional
    @Query("update Session s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    int revokeAllForUser(UUID userId, Instant now);
}
