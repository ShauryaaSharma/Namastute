package com.namastute.groups;

import com.namastute.notifications.PushService;
import com.namastute.notifications.PushService.Push;
import com.namastute.shared.ApiException;
import com.namastute.trips.TripService;
import com.namastute.trips.TripService.TripSummary;
import com.namastute.users.UserService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Join requests and the group state machine (design 3.2, 3.4). Plain SQL: almost every step is one conditional UPDATE
// whose WHERE names the state it starts from, so an invalid move changes 0 rows and we answer with an error.
// Public only for what other modules call (isActiveMember, memberIds, ...); everything else stays package-private
@Service
public class GroupService {

    static final int CAR_BAGS = 4;   // a sedan (design 3.3); seats are limited by groups.capacity
    // Live location at the pickup: from 20 minutes before the meeting time to 30 after (Learning Notes 60)
    static final Duration SHARE_BEFORE = Duration.ofMinutes(20), SHARE_AFTER = Duration.ofMinutes(30);

    record RequestView(UUID id, UUID fromTripId, String firstName, String destArea, long minutesAfterYou, int seats, int bags,
                       UserService.Rating rating) {}
    record SentView(UUID id, UUID toTripId, String firstName, @Schema(allowableValues = {"pending", "declined"}) String status) {}
    record Requests(List<SentView> sent, List<RequestView> received) {}
    // share: what this member owes the booker (the booker's own share is what they paid themselves); null until the fare
    // paid: the member said they've paid the booker, their own word only (Learning Notes 92)
    // tripId: an opaque handle for report/block (Learning Notes 56), never the user id
    record MemberView(UUID tripId, String firstName, String destArea, int seats, int bags, boolean booker, boolean you,
                      boolean here, @Schema(nullable = true) BigDecimal share, boolean paid, UserService.Rating rating) {}
    // meetAt: when the group meets at the pickup; sharingFrom/Until: when live location works (locked or met groups only)
    record GroupView(UUID id, @Schema(allowableValues = {"open", "locked", "met"}) String status, boolean youAreBooker,
                     @Schema(nullable = true) String bookerUpiId, @Schema(nullable = true) BigDecimal fareTotal, List<MemberView> members,
                     OffsetDateTime meetAt, OffsetDateTime sharingFrom, OffsetDateTime sharingUntil, boolean womenOnly) {}
    record ShareView(String firstName, boolean booker, BigDecimal share) {}

    record SentRow(UUID id, UUID toTripId, String status) {}
    record Pending(UUID id, UUID fromTripId, UUID toTripId) {}
    record Member(UUID userId, UUID tripId) {}
    record GroupRow(String status, UUID bookerId, BigDecimal fareTotal) {}
    record MemberRow(UUID userId, UUID tripId, OffsetDateTime hereAt, BigDecimal share, OffsetDateTime paidAt) {}
    record MyPart(UUID tripId, boolean booker, boolean fareIn, boolean paid) {}

    private final JdbcClient db;
    private final TripService trips;
    private final UserService users;
    private final ApplicationEventPublisher events;
    private final PushService push;

    GroupService(JdbcClient db, TripService trips, UserService users, ApplicationEventPublisher events, PushService push) {
        this.db = db;
        this.trips = trips;
        this.users = users;
        this.events = events;
        this.push = push;
    }

    // "Ask to share": from your searching trip to theirs (alone, or in a group that's still open)
    UUID ask(UUID userId, UUID toTripId) {
        TripSummary mine = searchingTrip(userId);
        TripSummary theirs = trips.summaries(List.of(toTripId)).stream()
                .filter(t -> !t.userId().equals(userId) && t.terminalId().equals(mine.terminalId()))
                .filter(t -> t.status().equals("searching") || groupOf(t.id()).filter(g -> g.status().equals("open")).isPresent())
                // Silent block: if either side blocked the other (or anyone in their group), it looks like an ended trip
                .filter(t -> !users.blockedEitherWay(userId, peopleAround(t)))
                // Women-only: the same answer as for a trip that doesn't exist, so a crafted request learns nothing
                .filter(t -> womenOnlyMismatch(mine, t).isEmpty())
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "That traveller isn't available any more"));
        try {
            UUID id = db.sql("insert into join_requests (from_trip_id, to_trip_id) values (?, ?) returning id")
                    .params(mine.id(), theirs.id()).query(UUID.class).single();
            groupOf(theirs.id()).ifPresent(g -> events.publishEvent(new GroupChanged(g.groupId(), null)));   // members see it live
            // Lock-screen text: a first name, nothing else (Learning Notes 58.5)
            push.send(peopleAround(theirs).stream().distinct().toList(),
                    new Push(nameOf(userId) + " wants to share a cab", "Open Namastute to answer", Duration.ofHours(1)));
            return id;
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
        Map<UUID, UserService.Rating> askerRatings = users.ratings(askers.values().stream().map(TripSummary::userId).toList());
        List<RequestView> received = pending.stream().map(p -> {
            TripSummary t = askers.get(p.fromTripId());
            return new RequestView(p.id(), p.fromTripId(), askerNames.get(t.userId()), t.destArea(),
                    Duration.between(mine.get().exitAt(), t.exitAt()).toMinutes(), t.seats(), t.bags(), askerRatings.get(t.userId()));
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
        // Checked again here, not only when asking: a request to someone alone stays pending after they join a group
        womenOnlyMismatch(from, to).ifPresent(reason -> {
            throw conflict("WOMEN_ONLY", reason);
        });
        UUID groupId;
        if (existing.isEmpty()) {
            // The person asked was alone: the group is born now, and the asker is its maker (= booker).
            // Both trips move in ONE statement, so two crossing requests lock the two rows in the same order (no deadlock).
            if (trips.markGrouped(List.of(from.id(), to.id())) != 2) {
                throw conflict("ALREADY_GROUPED", "One of you just joined another group");
            }
            groupId = db.sql("""
                            insert into groups (terminal_id, meet_at, women_only, seats_taken, bags_taken, booker_id)
                            values (?, ?, ?, ?, ?, ?) returning id""")
                    // Women-only if either of them asked for it, for the group's whole life
                    .params(to.terminalId(), to.exitAt().atOffset(ZoneOffset.UTC), from.womenOnly() || to.womenOnly(),
                            to.seats(), to.bags(), from.userId())
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
        events.publishEvent(new GroupChanged(groupId, firstName(from.userId()) + " joined"));
        push.send(List.of(from.userId()),
                new Push("You're in a cab group", nameOf(userId) + " said yes. Open Namastute to chat.", Duration.ofHours(1)));
    }

    void decline(UUID userId, UUID requestId) {
        Pending r = pendingRequest(requestId);
        Optional<Membership> group = answerableGroup(userId, byId(List.of(r.toTripId())).get(r.toTripId()));
        db.sql("update join_requests set status = 'declined' where id = ? and status = 'pending'").param(requestId).update();
        group.ifPresent(g -> events.publishEvent(new GroupChanged(g.groupId(), null)));
    }

    // Only the maker (= booker) locks: "I'm booking the cab now, no more people"
    @Transactional
    void lock(UUID userId, UUID groupId) {
        if (db.sql("update groups set status = 'locked' where id = ? and status = 'open' and booker_id = ?")
                .params(groupId, userId).update() == 0) {
            throw conflict("CANNOT_LOCK", "Only the person who started an open group can lock it");
        }
        declineWaitingRequests(groupId);
        events.publishEvent(new GroupChanged(groupId, firstName(userId) + " locked the group and is booking the cab"));
    }

    // ---- Finishing the ride (Learning Notes 50) ----

    // "I'm here" at the pickup point. Once 2+ members are here, the group moves locked → met.
    @Transactional
    void here(UUID userId, UUID groupId) {
        // Lock the group row first: two people tapping at the same moment would otherwise each count only themselves
        // (READ COMMITTED doesn't show the other's uncommitted tap) and the group would never become met (Learning Notes 40)
        GroupRow g = db.sql("select status, booker_id, fare_total from groups where id = ? for update")
                .param(groupId).query(GroupRow.class).optional()
                .filter(x -> isActiveMember(groupId, userId))
                .orElseThrow(GroupService::groupNotFound);
        if (g.status().equals("open")) {
            throw conflict("NOT_LOCKED", "Tap this at the pickup point, once the booker has locked the group");
        }
        int marked = db.sql("update group_members set here_at = now() where group_id = ? and user_id = ? and status = 'accepted' and here_at is null")
                .params(groupId, userId).update();
        if (marked == 0) {
            return;   // already here: tapping twice changes nothing
        }
        events.publishEvent(new GroupChanged(groupId, firstName(userId) + " is here"));
        int met = db.sql("""
                        update groups set status = 'met'
                        where id = ? and status = 'locked'
                          and (select count(*) from group_members where group_id = ? and status = 'accepted' and here_at is not null) >= 2""")
                .params(groupId, groupId).update();
        if (met == 1) {
            events.publishEvent(new GroupChanged(groupId, "You've met up. Have a good ride! The booker enters the fare afterwards."));
        }
    }

    // What everyone would pay for this fare, shown to the booker before confirming. Nothing is saved.
    List<ShareView> farePreview(UUID userId, UUID groupId, BigDecimal fare) {
        if (db.sql("select count(*) from groups where id = ? and status = 'met' and booker_id = ? and fare_total is null")
                .params(groupId, userId).query(Integer.class).single() == 0) {
            throw cannotEnterFare();
        }
        Map<UUID, BigDecimal> shares = split(groupId, fare, userId);
        Map<UUID, String> names = users.firstNames(List.copyOf(shares.keySet()));
        return shares.entrySet().stream()
                .map(e -> new ShareView(names.get(e.getKey()), e.getKey().equals(userId), e.getValue())).toList();
    }

    // The booker enters the real fare: once, only after meeting up (one conditional UPDATE checks all of it)
    @Transactional
    void enterFare(UUID userId, UUID groupId, BigDecimal fare) {
        if (db.sql("update groups set fare_total = ? where id = ? and status = 'met' and booker_id = ? and fare_total is null")
                .params(fare, groupId, userId).update() == 0) {
            throw cannotEnterFare();
        }
        Map<UUID, BigDecimal> shares = split(groupId, fare, userId);
        shares.forEach((member, share) ->
                db.sql("update group_members set share = ? where group_id = ? and user_id = ?").params(share, groupId, member).update());
        events.publishEvent(new GroupChanged(groupId, "Fare entered: ₹" + fare.toPlainString() + ". Check your share and pay the booker."));
        // No amount on the lock screen
        push.send(shares.keySet().stream().filter(m -> !m.equals(userId)).toList(),
                new Push("Your share of the cab fare is ready", "Open Namastute to see it and pay the booker", Duration.ofDays(1)));
    }

    // Everyone still in the group, weighted by their solo km (design 3.8)
    private Map<UUID, BigDecimal> split(UUID groupId, BigDecimal fare, UUID booker) {
        List<Member> members = db.sql("select user_id, trip_id from group_members where group_id = ? and status = 'accepted' order by created_at, user_id")
                .param(groupId).query(Member.class).list();
        Map<UUID, TripSummary> memberTrips = byId(members.stream().map(Member::tripId).toList());
        Map<UUID, Double> soloKm = new LinkedHashMap<>();
        members.forEach(m -> soloKm.put(m.userId(), memberTrips.get(m.tripId()).soloKm()));
        return FareSplit.split(fare, soloKm, booker);
    }

    private static ApiException cannotEnterFare() {
        return conflict("CANNOT_ENTER_FARE", "Only the booker can enter the fare, once, after the group has met up");
    }

    // ---- After the ride (Learning Notes 92) ----

    // "Done": once the fare is in, your own trip ends, so you can plan the next one straight away. The group stays as it
    // is for the others, and the 4-hour sweep (finish) closes it. Tapping twice changes nothing.
    @Transactional
    void done(UUID userId, UUID groupId) {
        MyPart me = myPart(userId, groupId);
        if (!me.fareIn()) {
            throw conflict("FARE_NOT_IN", "You can finish once the booker has entered the fare");
        }
        trips.markDone(List.of(me.tripId()));
    }

    // "I've paid": the member's own word that they paid the booker, shown to the group. Namastute never sees or checks
    // payments, so this is information, not proof. Saying it twice changes nothing.
    @Transactional
    void paid(UUID userId, UUID groupId) {
        MyPart me = myPart(userId, groupId);
        if (me.booker()) {
            throw conflict("BOOKER_DOESNT_OWE", "You booked the cab: the others pay you");
        }
        if (!me.fareIn()) {
            throw conflict("FARE_NOT_IN", "You can pay once the booker has entered the fare");
        }
        if (me.paid() || db.sql("update group_members set paid_at = now() where group_id = ? and user_id = ? and paid_at is null")
                .params(groupId, userId).update() == 0) {
            return;
        }
        events.publishEvent(new GroupChanged(groupId, firstName(userId) + " says they've paid their share"));
        UUID booker = db.sql("select booker_id from groups where id = ?").param(groupId).query(UUID.class).single();
        // No amount on the lock screen (Learning Notes 58.5)
        push.send(List.of(booker), new Push(nameOf(userId) + " says they've paid",
                "Check your UPI app to be sure: Namastute never sees payments", Duration.ofDays(1)));
    }

    // Your place in an active group: anyone else gets the same 404 as a missing group. fareIn = met and the fare entered.
    private MyPart myPart(UUID userId, UUID groupId) {
        return db.sql("""
                        select m.trip_id, m.user_id = g.booker_id as booker, g.status = 'met' and g.fare_total is not null as fare_in,
                               m.paid_at is not null as paid
                        from group_members m join groups g on g.id = m.group_id
                        where g.id = ? and m.user_id = ? and m.status = 'accepted' and g.status in ('open', 'locked', 'met')""")
                .params(groupId, userId).query(MyPart.class).optional()
                .orElseThrow(GroupService::groupNotFound);
    }

    // ---- Background jobs (Learning Notes 48), run every minute by GroupJobs ----
    // Each is a conditional UPDATE ... RETURNING id: only groups this run actually changed are followed up and
    // announced, so a second run or a second server does nothing twice (idempotent). now() = the database's clock.

    // open, and the meeting was over 30 minutes ago (missed, e.g. while the server was down) → expired
    @Transactional
    void expireUnformed() {
        List<UUID> ids = db.sql("update groups set status = 'expired' where status = 'open' and meet_at < now() - interval '30 minutes' returning id")
                .query(UUID.class).list();
        if (!ids.isEmpty()) {
            trips.markExpired(memberTrips(ids));
            ids.forEach(id -> events.publishEvent(new GroupChanged(id, null)));
        }
    }

    // open, and meeting within 5 minutes → locked, so nobody joins at the last second
    @Transactional
    void autoLock() {
        List<UUID> ids = db.sql("update groups set status = 'locked' where status = 'open' and meet_at <= now() + interval '5 minutes' returning id")
                .query(UUID.class).list();
        for (UUID id : ids) {
            declineWaitingRequests(id);
            events.publishEvent(new GroupChanged(id, "Locked automatically: meeting in 5 minutes"));
        }
    }

    // locked or met, and the meeting was over 4 hours ago → done (design 3.2: "or meet_at + 4 h").
    // Entering the fare doesn't end the group: members keep seeing their share until this runs.
    @Transactional
    void finish() {
        List<UUID> ids = db.sql("update groups set status = 'done' where status in ('locked', 'met') and meet_at < now() - interval '4 hours' returning id")
                .query(UUID.class).list();
        if (!ids.isEmpty()) {
            trips.markDone(memberTrips(ids));
            ids.forEach(id -> events.publishEvent(new GroupChanged(id, null)));
        }
    }

    // ---- For the feedback module (report and block, Learning Notes 56) ----

    // The group the reporter shared with the owner of this trip (both accepted, or since left), most recent first
    public Optional<UUID> sharedGroup(UUID reporter, UUID reportedTripId) {
        return db.sql("""
                        select them.group_id from group_members them
                        join group_members me on me.group_id = them.group_id and me.user_id = ? and me.status in ('accepted', 'left')
                        where them.trip_id = ? and them.status in ('accepted', 'left')
                        order by them.created_at desc limit 1""")
                .params(reporter, reportedTripId).query(UUID.class).optional();
    }

    // After a block: pending requests between the two people's current trips quietly disappear
    // shortcut: a request from the blocked person to ANOTHER member of the blocker's group stays visible; decline it
    public void withdrawRequestsBetween(UUID a, UUID b) {
        Optional<UUID> tripA = trips.activeTrip(a).map(TripSummary::id);
        Optional<UUID> tripB = trips.activeTrip(b).map(TripSummary::id);
        if (tripA.isPresent() && tripB.isPresent()) {
            db.sql("""
                            update join_requests set status = 'withdrawn'
                            where status = 'pending' and ((from_trip_id = :a and to_trip_id = :b) or (from_trip_id = :b and to_trip_id = :a))""")
                    .param("a", tripA.get()).param("b", tripB.get()).update();
        }
    }

    // The trip's owner plus everyone in their open group
    private List<UUID> peopleAround(TripSummary trip) {
        List<UUID> people = new ArrayList<>(List.of(trip.userId()));
        groupOf(trip.id()).ifPresent(g -> people.addAll(
                db.sql("select user_id from group_members where group_id = ? and status = 'accepted'").param(g.groupId()).query(UUID.class).list()));
        return people;
    }

    private List<UUID> memberTrips(List<UUID> groupIds) {
        return db.sql("select trip_id from group_members where group_id in (:ids) and status = 'accepted'")
                .param("ids", groupIds).query(UUID.class).list();
    }

    // A locked group takes nobody new, so waiting requests get a clear "no"
    private void declineWaitingRequests(UUID groupId) {
        db.sql("""
                update join_requests set status = 'declined'
                where status = 'pending'
                  and to_trip_id in (select trip_id from group_members where group_id = ? and status = 'accepted')""")
                .param(groupId).update();
    }

    @Transactional
    void leave(UUID userId, UUID groupId, String reason) {
        // Pessimistic lock on the group row: we read it, then decide several writes (Learning Notes 40.5)
        GroupRow g = db.sql("select status, booker_id, fare_total from groups where id = ? and status in ('open', 'locked', 'met') for update")
                .param(groupId).query(GroupRow.class).optional()
                .orElseThrow(GroupService::groupNotFound);
        if (g.status().equals("met")) {
            throw conflict("ALREADY_MET", "You've met up already, so the ride is on: settle the fare instead of leaving");
        }
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

        if (wasLocked && !userId.equals(g.bookerId())) {
            // The feedback module files this as a report on the booker (an event, so groups never depends on feedback)
            events.publishEvent(new MemberLeftLockedGroup(groupId, userId, g.bookerId(), reason.strip()));
        }
        // The chat module turns this into a system message ("Asha left: …") once the transaction commits
        events.publishEvent(new GroupChanged(groupId, firstName(userId) + " left" + (wasLocked ? ": " + reason.strip() : "")));
    }

    // Your open, locked or met group: members, who's here, the fare and shares, the booker's UPI ID (members only, design 4.4)
    Optional<GroupView> current(UUID userId) {
        Optional<Membership> membership = trips.activeTrip(userId).flatMap(t -> groupOf(t.id()));
        if (membership.isEmpty()) {
            return Optional.empty();
        }
        UUID groupId = membership.get().groupId();
        GroupRow g = db.sql("select status, booker_id, fare_total from groups where id = ?").param(groupId).query(GroupRow.class).single();
        List<MemberRow> members = db.sql("""
                        select user_id, trip_id, here_at, share, paid_at from group_members
                        where group_id = ? and status = 'accepted' order by created_at, user_id""")
                .param(groupId).query(MemberRow.class).list();
        Map<UUID, TripSummary> memberTrips = byId(members.stream().map(MemberRow::tripId).toList());
        Map<UUID, String> names = users.firstNames(members.stream().map(MemberRow::userId).toList());
        Map<UUID, UserService.Rating> ratings = users.ratings(members.stream().map(MemberRow::userId).toList());
        List<MemberView> views = members.stream().map(m -> {
            TripSummary t = memberTrips.get(m.tripId());
            return new MemberView(m.tripId(), names.get(m.userId()), t.destArea(), t.seats(), t.bags(),
                    m.userId().equals(g.bookerId()), m.userId().equals(userId), m.hereAt() != null, m.share(), m.paidAt() != null,
                    ratings.get(m.userId()));
        }).toList();
        OffsetDateTime meetAt = db.sql("select meet_at from groups where id = ?").param(groupId).query(OffsetDateTime.class).single();
        return Optional.of(new GroupView(groupId, g.status(), userId.equals(g.bookerId()), users.upiId(g.bookerId()),
                g.fareTotal(), views, meetAt, meetAt.minus(SHARE_BEFORE), meetAt.plus(SHARE_AFTER), membership.get().womenOnly()));
    }

    // For the chat module: may this user read and write this group's chat? (accepted member of an open, locked or met group)
    public boolean isActiveMember(UUID groupId, UUID userId) {
        return db.sql("""
                        select count(*) from group_members m join groups g on g.id = m.group_id
                        where m.group_id = ? and m.user_id = ? and m.status = 'accepted' and g.status in ('open', 'locked', 'met')""")
                .params(groupId, userId).query(Integer.class).single() > 0;
    }

    // For the chat module: your trip (the handle others see) if you may share your location right now:
    // an accepted member of a locked or met group, inside the pickup window
    public Optional<UUID> sharingTrip(UUID groupId, UUID userId) {
        return db.sql("""
                        select m.trip_id from group_members m join groups g on g.id = m.group_id
                        where m.group_id = ? and m.user_id = ? and m.status = 'accepted' and g.status in ('locked', 'met')
                          and now() between g.meet_at - make_interval(mins => ?) and g.meet_at + make_interval(mins => ?)""")
                .params(groupId, userId, (int) SHARE_BEFORE.toMinutes(), (int) SHARE_AFTER.toMinutes())
                .query(UUID.class).optional();
    }

    // ---- For the feedback module: ratings (Learning Notes 62) ----

    public record RideTogether(UUID groupId, UUID userId) {}

    // The group you and this trip's owner both rode in, if it reached "met" (and is still open for ratings)
    public Optional<RideTogether> metWith(UUID me, UUID theirTripId) {
        return db.sql("""
                        select them.group_id, them.user_id from group_members them
                        join group_members me on me.group_id = them.group_id and me.user_id = ? and me.status = 'accepted'
                        join groups g on g.id = them.group_id and g.status = 'met'
                        where them.trip_id = ? and them.status = 'accepted' and them.user_id <> ?""")
                .params(me, theirTripId, me).query(RideTogether.class).optional();
    }

    // Each member's trip handle, so ratings (stored by user) can be shown against the group card's handles
    public Map<UUID, UUID> memberTripsByUser(UUID groupId) {
        return db.sql("select user_id, trip_id from group_members where group_id = ?").param(groupId)
                .query(Member.class).list().stream().collect(Collectors.toMap(Member::userId, Member::tripId));
    }

    // For the chat module: who to notify about a new message
    public List<UUID> memberIds(UUID groupId) {
        return db.sql("select user_id from group_members where group_id = ? and status = 'accepted'").param(groupId).query(UUID.class).list();
    }

    record Membership(UUID groupId, String status, boolean womenOnly) {}

    // The open, locked or met group this trip is an accepted member of, if any
    private Optional<Membership> groupOf(UUID tripId) {
        return db.sql("""
                        select g.id as group_id, g.status, g.women_only from group_members m join groups g on g.id = m.group_id
                        where m.trip_id = ? and m.status = 'accepted' and g.status in ('open', 'locked', 'met')""")
                .param(tripId).query(Membership.class).optional();
    }

    // Why `from` can't join `to` (alone, or their group) under the women-only rule, if it can't. The same rule as matching
    // (design 4.3): a women-only trip or group takes only eligible women, and someone who asked for women-only joins only
    // a women-only group, or an eligible woman who is still alone.
    private Optional<String> womenOnlyMismatch(TripSummary from, TripSummary to) {
        Optional<Membership> group = groupOf(to.id());
        boolean toWomenOnly = group.map(Membership::womenOnly).orElse(to.womenOnly());
        if (toWomenOnly && !users.womenOnlyAllowed(from.userId())) {
            return Optional.of("This group is women-only, for women with a verified ID");
        }
        if (from.womenOnly() && !(group.isPresent() ? toWomenOnly : users.womenOnlyAllowed(to.userId()))) {
            return Optional.of("They asked for a women-only group");
        }
        return Optional.empty();
    }

    // Who may answer (team decision, Learning Notes 39.2): if the person asked is alone, only them;
    // if they're in a group, any accepted member. Anyone else gets the same 404 as a missing request.
    private Optional<Membership> answerableGroup(UUID userId, TripSummary to) {
        Optional<Membership> group = groupOf(to.id());
        boolean allowed = group.map(g -> isActiveMember(g.groupId(), userId)).orElse(to.userId().equals(userId));
        if (!allowed) {
            throw requestNotFound();
        }
        return group;
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

    private String firstName(UUID userId) {
        return users.firstNames(List.of(userId)).get(userId);
    }

    private String nameOf(UUID userId) {
        return Objects.requireNonNullElse(firstName(userId), "Someone");   // the name is optional (V2)
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
