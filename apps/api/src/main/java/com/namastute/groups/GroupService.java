package com.namastute.groups;

import com.namastute.shared.ApiException;
import com.namastute.trips.TripService;
import com.namastute.trips.TripService.TripSummary;
import com.namastute.users.UserService;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Join requests and the group state machine (design 3.2, 3.4). Plain SQL: almost every step is one conditional UPDATE
// whose WHERE names the state it starts from, so an invalid move changes 0 rows and we answer with an error.
@Service
class GroupService {

    static final int CAR_BAGS = 4;   // a sedan (design 3.3); seats are limited by groups.capacity

    record RequestView(UUID id, String firstName, String destArea, long minutesAfterYou, int seats, int bags) {}
    record SentView(UUID id, UUID toTripId, String firstName, String status) {}
    record Requests(List<SentView> sent, List<RequestView> received) {}
    record MemberView(String firstName, String destArea, int seats, int bags, boolean booker, boolean you) {}
    record GroupView(UUID id, String status, boolean youAreBooker, String bookerUpiId, List<MemberView> members,
                     List<String> updates) {}

    record SentRow(UUID id, UUID toTripId, String status) {}
    record Pending(UUID id, UUID fromTripId, UUID toTripId) {}
    record Member(UUID userId, UUID tripId) {}
    record GroupRow(String status, UUID bookerId) {}

    private final JdbcClient db;
    private final TripService trips;
    private final UserService users;

    GroupService(JdbcClient db, TripService trips, UserService users) {
        this.db = db;
        this.trips = trips;
        this.users = users;
    }

    // "Ask to share": from your searching trip to theirs (alone, or in a group that's still open)
    UUID ask(UUID userId, UUID toTripId) {
        TripSummary mine = searchingTrip(userId);
        TripSummary theirs = trips.summaries(List.of(toTripId)).stream()
                .filter(t -> !t.userId().equals(userId) && t.terminalId().equals(mine.terminalId()))
                .filter(t -> t.status().equals("searching") || groupOf(t.id()).filter(g -> g.status().equals("open")).isPresent())
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "That traveller isn't available any more"));
        try {
            return db.sql("insert into join_requests (from_trip_id, to_trip_id) values (?, ?) returning id")
                    .params(mine.id(), theirs.id()).query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_ASKED", "You've already asked this traveller");
        }
    }

    // Requests you sent, and requests you may answer: to you, or to anyone in your open group
    Requests requests(UUID userId) {
        Optional<TripSummary> mine = trips.activeTrip(userId);
        if (mine.isEmpty()) {
            return new Requests(List.of(), List.of());
        }
        UUID myTrip = mine.get().id();

        List<SentRow> rows = db.sql("""
                        select id, to_trip_id, status from join_requests
                        where from_trip_id = ? and status in ('pending', 'declined') order by created_at""")
                .param(myTrip).query(SentRow.class).list();
        Map<UUID, TripSummary> targets = byId(rows.stream().map(SentRow::toTripId).toList());
        Map<UUID, String> targetNames = users.firstNames(targets.values().stream().map(TripSummary::userId).toList());
        List<SentView> sent = rows.stream().map(s -> new SentView(s.id(), s.toTripId(),
                targetNames.get(targets.get(s.toTripId()).userId()), s.status())).toList();

        List<Pending> pending = db.sql("""
                        select r.id, r.from_trip_id, r.to_trip_id from join_requests r
                        where r.status = 'pending' and (r.to_trip_id = :trip or r.to_trip_id in (
                            select m2.trip_id from group_members m
                            join groups g on g.id = m.group_id and g.status = 'open'
                            join group_members m2 on m2.group_id = m.group_id and m2.status = 'accepted'
                            where m.trip_id = :trip and m.status = 'accepted'))
                        order by r.created_at""")
                .param("trip", myTrip)
                .query(Pending.class)
                .list();
        Map<UUID, TripSummary> askers = byId(pending.stream().map(Pending::fromTripId).toList());
        Map<UUID, String> askerNames = users.firstNames(askers.values().stream().map(TripSummary::userId).toList());
        List<RequestView> received = pending.stream().map(p -> {
            TripSummary t = askers.get(p.fromTripId());
            return new RequestView(p.id(), askerNames.get(t.userId()), t.destArea(),
                    Duration.between(mine.get().exitAt(), t.exitAt()).toMinutes(), t.seats(), t.bags());
        }).toList();
        return new Requests(sent, received);
    }

    // Saying yes. All-or-nothing: if any step fails, the whole transaction rolls back (Learning Notes 40.3).
    @Transactional
    void accept(UUID userId, UUID requestId) {
        Pending r = pendingRequest(requestId);
        Map<UUID, TripSummary> t = byId(List.of(r.fromTripId(), r.toTripId()));
        TripSummary from = t.get(r.fromTripId());
        TripSummary to = t.get(r.toTripId());

        Optional<Membership> existing = answerableGroup(userId, to);
        UUID groupId;
        if (existing.isEmpty()) {
            // The person asked was alone: the group is born now, and the asker is its maker (= booker).
            // Both trips move in ONE statement, so two crossing requests lock the two rows in the same order (no deadlock).
            if (trips.markGrouped(List.of(from.id(), to.id())) != 2) {
                throw conflict("ALREADY_GROUPED", "One of you just joined another group");
            }
            groupId = db.sql("""
                            insert into groups (terminal_id, meet_at, women_only, seats_taken, bags_taken, booker_id)
                            values (?, ?, false, ?, ?, ?) returning id""")
                    .params(to.terminalId(), to.exitAt().atOffset(ZoneOffset.UTC), to.seats(), to.bags(), from.userId())
                    .query(UUID.class).single();
            addMember(groupId, to);
        } else {
            groupId = existing.get().groupId();
        }

        // The last-seat race: check and change in ONE statement (design 3.4, Learning Notes 40.4)
        int seated = db.sql("""
                        update groups set seats_taken = seats_taken + :seats, bags_taken = bags_taken + :bags,
                                          meet_at = greatest(meet_at, :exitAt)
                        where id = :id and status = 'open'
                          and seats_taken + :seats <= capacity and bags_taken + :bags <= :maxBags""")
                .param("seats", from.seats()).param("bags", from.bags()).param("exitAt", from.exitAt().atOffset(ZoneOffset.UTC))
                .param("id", groupId).param("maxBags", CAR_BAGS)
                .update();
        if (seated == 0) {
            throw conflict("GROUP_FULL", "Not enough room left in this cab");
        }
        if (db.sql("update join_requests set status = 'accepted' where id = ? and status = 'pending'").param(requestId).update() == 0) {
            throw conflict("REQUEST_ANSWERED", "Someone already answered this request");
        }
        if (existing.isPresent() && trips.markGrouped(List.of(from.id())) != 1) {
            throw conflict("ALREADY_GROUPED", "They just joined another group");
        }
        addMember(groupId, from);
        // Nobody can be in two groups: both people's other requests are withdrawn
        db.sql("update join_requests set status = 'withdrawn' where status = 'pending' and from_trip_id in (?, ?)")
                .params(from.id(), to.id()).update();
    }

    void decline(UUID userId, UUID requestId) {
        Pending r = pendingRequest(requestId);
        answerableGroup(userId, byId(List.of(r.toTripId())).get(r.toTripId()));
        db.sql("update join_requests set status = 'declined' where id = ? and status = 'pending'").param(requestId).update();
    }

    // Only the maker (= booker) locks: "I'm booking the cab now, no more people"
    @Transactional
    void lock(UUID userId, UUID groupId) {
        if (db.sql("update groups set status = 'locked' where id = ? and status = 'open' and booker_id = ?")
                .params(groupId, userId).update() == 0) {
            throw conflict("CANNOT_LOCK", "Only the person who started an open group can lock it");
        }
        // Nobody else can join now, so waiting requests get a clear "no"
        db.sql("""
                update join_requests set status = 'declined'
                where status = 'pending'
                  and to_trip_id in (select trip_id from group_members where group_id = ? and status = 'accepted')""")
                .param(groupId).update();
    }

    @Transactional
    void leave(UUID userId, UUID groupId, String reason) {
        // Pessimistic lock on the group row: we read it, then decide several writes (Learning Notes 40.5)
        GroupRow g = db.sql("select status, booker_id from groups where id = ? and status in ('open', 'locked') for update")
                .param(groupId).query(GroupRow.class).optional()
                .orElseThrow(GroupService::groupNotFound);
        boolean wasLocked = g.status().equals("locked");
        if (wasLocked && (reason == null || reason.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "Tell your group why you're leaving a locked group");
        }
        UUID tripId = db.sql("update group_members set status = 'left' where group_id = ? and user_id = ? and status = 'accepted' returning trip_id")
                .params(groupId, userId).query(UUID.class).optional()
                .orElseThrow(GroupService::groupNotFound);
        TripSummary mine = byId(List.of(tripId)).get(tripId);
        db.sql("update groups set seats_taken = seats_taken - ?, bags_taken = bags_taken - ? where id = ?")
                .params(mine.seats(), mine.bags(), groupId).update();
        trips.markSearching(List.of(tripId));

        List<Member> left = db.sql("select user_id, trip_id from group_members where group_id = ? and status = 'accepted' order by created_at, user_id")
                .param(groupId).query(Member.class).list();
        if (left.size() < 2) {
            // A group needs two people: the last one goes back to searching too
            db.sql("update groups set status = 'cancelled', seats_taken = 0, bags_taken = 0 where id = ?").param(groupId).update();
            db.sql("update group_members set status = 'left' where group_id = ? and status = 'accepted'").param(groupId).update();
            if (!left.isEmpty()) {
                trips.markSearching(left.stream().map(Member::tripId).toList());
            }
        } else if (userId.equals(g.bookerId())) {
            // The maker left: the longest-standing member takes over, and a locked group re-opens
            db.sql("update groups set booker_id = ?, status = 'open' where id = ?").params(left.get(0).userId(), groupId).update();
        }

        if (wasLocked) {
            String why = reason.strip();
            // shortcut: writes the chat module's messages and feedback module's reports tables directly;
            // go through those modules once they exist (slice 3 and later)
            db.sql("insert into messages (group_id, kind, body) values (?, 'system', ?)")
                    .params(groupId, users.firstNames(List.of(userId)).get(userId) + " left: " + why).update();
            if (!userId.equals(g.bookerId())) {
                db.sql("insert into reports (reporter_id, reported_id, group_id, reason) values (?, ?, ?, ?)")
                        .params(userId, g.bookerId(), groupId, why).update();
            }
        }
    }

    // Your open or locked group: members, the booker's UPI ID (members only, design 4.4) and recent updates
    Optional<GroupView> current(UUID userId) {
        Optional<Membership> membership = trips.activeTrip(userId).flatMap(t -> groupOf(t.id()));
        if (membership.isEmpty()) {
            return Optional.empty();
        }
        UUID groupId = membership.get().groupId();
        GroupRow g = db.sql("select status, booker_id from groups where id = ?").param(groupId).query(GroupRow.class).single();
        List<Member> members = db.sql("select user_id, trip_id from group_members where group_id = ? and status = 'accepted' order by created_at, user_id")
                .param(groupId).query(Member.class).list();
        Map<UUID, TripSummary> memberTrips = byId(members.stream().map(Member::tripId).toList());
        Map<UUID, String> names = users.firstNames(members.stream().map(Member::userId).toList());
        List<MemberView> views = members.stream().map(m -> {
            TripSummary t = memberTrips.get(m.tripId());
            return new MemberView(names.get(m.userId()), t.destArea(), t.seats(), t.bags(),
                    m.userId().equals(g.bookerId()), m.userId().equals(userId));
        }).toList();
        List<String> updates = db.sql("select body from messages where group_id = ? and kind = 'system' order by id desc limit 5")
                .param(groupId).query(String.class).list();
        return Optional.of(new GroupView(groupId, g.status(), userId.equals(g.bookerId()), users.upiId(g.bookerId()),
                views, updates));
    }

    record Membership(UUID groupId, String status) {}

    // The open or locked group this trip is an accepted member of, if any
    private Optional<Membership> groupOf(UUID tripId) {
        return db.sql("""
                        select g.id as group_id, g.status from group_members m join groups g on g.id = m.group_id
                        where m.trip_id = ? and m.status = 'accepted' and g.status in ('open', 'locked')""")
                .param(tripId).query(Membership.class).optional();
    }

    // Who may answer (team decision, Learning Notes 39.2): if the person asked is alone, only them;
    // if they're in a group, any accepted member. Anyone else gets the same 404 as a missing request.
    private Optional<Membership> answerableGroup(UUID userId, TripSummary to) {
        Optional<Membership> group = groupOf(to.id());
        boolean allowed = group.map(g -> isMember(g.groupId(), userId)).orElse(to.userId().equals(userId));
        if (!allowed) {
            throw requestNotFound();
        }
        return group;
    }

    private boolean isMember(UUID groupId, UUID userId) {
        return db.sql("select count(*) from group_members where group_id = ? and user_id = ? and status = 'accepted'")
                .params(groupId, userId).query(Integer.class).single() > 0;
    }

    private Pending pendingRequest(UUID requestId) {
        return db.sql("select id, from_trip_id, to_trip_id from join_requests where id = ? and status = 'pending'")
                .param(requestId).query(Pending.class).optional()
                .orElseThrow(GroupService::requestNotFound);
    }

    private void addMember(UUID groupId, TripSummary trip) {
        // "on conflict": someone who left this group earlier and is accepted again gets their row back
        db.sql("""
                        insert into group_members (group_id, user_id, trip_id, status) values (?, ?, ?, 'accepted')
                        on conflict (group_id, user_id) do update set status = 'accepted', trip_id = excluded.trip_id, created_at = now()""")
                .params(groupId, trip.userId(), trip.id()).update();
    }

    private TripSummary searchingTrip(UUID userId) {
        return trips.activeTrip(userId).filter(t -> t.status().equals("searching"))
                .orElseThrow(() -> conflict("NO_SEARCHING_TRIP", "You need a trip that's still looking for co-travellers"));
    }

    private Map<UUID, TripSummary> byId(List<UUID> tripIds) {
        return trips.summaries(tripIds).stream().collect(Collectors.toMap(TripSummary::id, Function.identity()));
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private static ApiException requestNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "REQUEST_NOT_FOUND", "That request isn't waiting for an answer");
    }

    private static ApiException groupNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "You're not in that group");
    }
}
