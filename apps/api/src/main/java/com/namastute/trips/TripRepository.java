package com.namastute.trips;

import com.namastute.users.UserService;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

    // Expires one user's stale trips right away, so a forgotten trip can't block a new one until the next sweep
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            update trip_requests set status = 'expired'
            where user_id = :userId and status = 'searching' and exit_at < :cutoff""")
    int expireStale(UUID userId, Instant cutoff);

    // The background sweep for everyone (design 3.7): the database's own clock decides what's stale
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            update trip_requests set status = 'expired'
            where status = 'searching' and exit_at < now() - interval '60 minutes'""")
    int expireAllStale();

    // grouped → done / expired, when the groups module finishes or expires a group
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "update trip_requests set status = 'done' where id in (:ids) and status = 'grouped'")
    int markDone(Collection<UUID> ids);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "update trip_requests set status = 'expired' where id in (:ids) and status = 'grouped'")
    int markExpired(Collection<UUID> ids);

    // Cancels only if it's this user's trip and still searching; returns 1 if cancelled, 0 otherwise
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            update trip_requests set status = 'cancelled'
            where id = :id and user_id = :userId and status = 'searching'""")
    int cancel(UUID id, UUID userId);

    // State machine moves for the groups module: only trips still in the "from" state change (returns how many did)
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "update trip_requests set status = 'grouped' where id in (:ids) and status = 'searching'")
    int markGrouped(Collection<UUID> ids);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "update trip_requests set status = 'searching' where id in (:ids) and status = 'grouped'")
    int markSearching(Collection<UUID> ids);

    // Stage 1 of matching: cheap filters the indexes can answer (trip_search, trip_dest_gist). MatchRanker does the rest.
    // Women-only works both ways (design 4.3): a women-only trip only meets eligible women, and only they see it.
    // shortcut: "destinations within 15 km" misses riders going much further down the same road;
    // filter by direction from the airport (bearing) once that matters.
    @Query(nativeQuery = true, value = """
            select t.* from trip_requests me
            join trip_requests t
              on t.terminal_id = me.terminal_id
             and t.status = 'searching'
             and t.exit_at between me.exit_at - interval '30 minutes' and me.exit_at + interval '30 minutes'
             and t.user_id <> me.user_id
             and ST_DWithin(t.dest, me.dest, 15000)
            where me.id = :tripId
              and not exists (select 1 from blocks b
                              where (b.blocker_id = me.user_id and b.blocked_id = t.user_id)
                                 or (b.blocker_id = t.user_id and b.blocked_id = me.user_id))
              and (not me.women_only or t.user_id in """ + UserService.WOMEN_ONLY_USERS + ")"
            + " and (not t.women_only or me.user_id in " + UserService.WOMEN_ONLY_USERS + ")")
    List<TripRequest> matchCandidates(UUID tripId);
}
