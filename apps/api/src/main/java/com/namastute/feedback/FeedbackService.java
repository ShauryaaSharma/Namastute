package com.namastute.feedback;

import com.namastute.groups.GroupService;
import com.namastute.groups.MemberLeftLockedGroup;
import com.namastute.shared.ApiException;
import com.namastute.trips.TripService;
import com.namastute.trips.TripService.TripSummary;
import com.namastute.users.UserService;
import com.namastute.users.UserService.BlockView;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Report, block (Learning Notes 56) and ratings (62). People are named by the opaque trip handles the app already shows,
// and every answer is the same whether a target doesn't exist or isn't yours to act on.
@Service
class FeedbackService {

    // What you gave in this ride, so the card shows "You gave 👍" instead of the buttons
    record MyRating(UUID tripId, boolean up) {}
    record RatingRow(UUID rateeId, boolean up) {}

    private final JdbcClient db;
    private final TripService trips;
    private final GroupService groups;
    private final UserService users;

    FeedbackService(JdbcClient db, TripService trips, GroupService groups, UserService users) {
        this.db = db;
        this.trips = trips;
        this.groups = groups;
        this.users = users;
    }

    // Anyone you can see (a match or a group member). Silent: the other person is never told.
    void block(UUID me, UUID tripId) {
        UUID them = ownerOf(tripId, me);
        users.block(me, them);
        groups.withdrawRequestsBetween(me, them);
    }

    List<BlockView> blocks(UUID me) {
        return users.blocks(me);
    }

    void unblock(UUID me, UUID blockId) {
        if (!users.unblock(me, blockId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BLOCK_NOT_FOUND", "That block doesn't exist");
        }
    }

    // Only someone you've shared a group with: the group is the evidence. One report per person per group.
    // shortcut: reports wait in the database for an admin (query in infra/README.md); an admin panel comes later
    void report(UUID me, UUID tripId, String category, String details) {
        UUID them = ownerOf(tripId, me);
        UUID group = groups.sharedGroup(me, tripId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_IN_A_GROUP_TOGETHER",
                        "You can only report someone you've shared a cab group with"));
        try {
            db.sql("insert into reports (reporter_id, reported_id, group_id, category, reason) values (?, ?, ?, ?, ?)")
                    .params(me, them, group, category, details == null ? "" : details.strip()).update();
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REPORTED", "You've already reported them for this ride");
        }
    }

    // A thumbs up or down for someone you met up with: once per person per ride (the primary key), final, and
    // never shown individually to anyone in the app. The row and the counters change in ONE transaction.
    @Transactional
    void rate(UUID me, UUID tripId, boolean up) {
        GroupService.RideTogether ride = groups.metWith(me, tripId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "NOT_RATEABLE", "You can rate people once you've met up for a ride together"));
        int added = db.sql("insert into ratings (group_id, rater_id, ratee_id, up) values (?, ?, ?, ?) on conflict do nothing")
                .params(ride.groupId(), me, ride.userId(), up).update();
        if (added == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_RATED", "You've already rated them for this ride");
        }
        users.addRating(ride.userId(), up);
    }

    List<MyRating> myRatings(UUID me, UUID groupId) {
        if (!groups.isActiveMember(groupId, me)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "You're not in that group");
        }
        Map<UUID, UUID> handles = groups.memberTripsByUser(groupId);
        return db.sql("select ratee_id, up from ratings where group_id = ? and rater_id = ?").params(groupId, me)
                .query(RatingRow.class).list().stream().map(r -> new MyRating(handles.get(r.rateeId()), r.up())).toList();
    }

    // Leaving a locked group with a reason files a report on the booker. Runs inside the groups transaction
    // (same connection, Learning Notes 51.5); "do nothing" if the member already reported the booker for this group.
    @EventListener
    void onMemberLeftLockedGroup(MemberLeftLockedGroup event) {
        db.sql("""
                        insert into reports (reporter_id, reported_id, group_id, category, reason) values (?, ?, ?, 'other', ?)
                        on conflict (reporter_id, reported_id, group_id) do nothing""")
                .params(event.memberId(), event.bookerId(), event.groupId(), event.reason()).update();
    }

    private UUID ownerOf(UUID tripId, UUID me) {
        return trips.summaries(List.of(tripId)).stream()
                .map(TripSummary::userId)
                .filter(owner -> !owner.equals(me))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRAVELLER_NOT_FOUND", "That traveller isn't available"));
    }
}
