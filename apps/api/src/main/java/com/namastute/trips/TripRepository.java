package com.namastute.trips;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

// The two updates are native SQL: Hibernate writes enum literals in JPQL as 'x'::Status (the Java class name),
// but our Postgres enum type is trip_status. In plain SQL, 'expired' is just text that Postgres converts itself.
interface TripRepository extends JpaRepository<TripRequest, UUID> {

    Optional<TripRequest> findFirstByUserIdAndStatusIn(UUID userId, Collection<TripRequest.Status> statuses);

    // shortcut: expires a user's stale trips on demand; a scheduled job (design 3.7) will do this for everyone
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            update trip_requests set status = 'expired'
            where user_id = :userId and status = 'searching' and exit_at < :cutoff""")
    int expireStale(UUID userId, Instant cutoff);

    // Cancels only if it's this user's trip and still searching; returns 1 if cancelled, 0 otherwise
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            update trip_requests set status = 'cancelled'
            where id = :id and user_id = :userId and status = 'searching'""")
    int cancel(UUID id, UUID userId);
}
