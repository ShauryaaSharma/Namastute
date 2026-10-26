package com.namastute.users;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The users module's public API: other modules (auth, trips, groups, chat, feedback) call these methods, never the repository
@Service
public class UserService {

    // What the blocker sees in "Blocked people": an opaque handle to unblock, and a first name. Never the user id.
    public record BlockView(UUID id, String firstName) {}
    record BlockRow(UUID id, UUID blockedId) {}

    // What others see next to a name: "92% 👍 · 12 ratings" and the "◆ ID verified" badge (Learning Notes 80).
    // upPercent stays null below MIN_RATINGS, so one early thumbs-down can't brand a newcomer.
    public record Rating(int count, @Schema(nullable = true) Integer upPercent, boolean idVerified) {}
    record RatingRow(UUID id, int ratingCount, int ratingSum, boolean verified) {}

    static final int MIN_RATINGS = 3;

    // Who may use women-only trips and groups (design 4.3, Learning Notes 84): an approved ID check whose reviewer
    // recorded "female" from the ID. Gender comes from the ID, never from a tick box. The ONE definition: matching
    // (TripRepository, OpenGroupsQuery) puts it in its SQL, and womenOnlyAllowed() below asks it.
    // shortcut: IDs showing transgender / third gender (gender 'other') aren't included; that policy is the team's open
    // decision (design open question 4), and changing this line changes every check.
    public static final String WOMEN_ONLY_USERS = "(select id from users where verification = 'verified' and gender = 'female')";

    private final UserRepository users;
    private final JdbcClient db;

    UserService(UserRepository users, JdbcClient db) {
        this.users = users;
        this.db = db;
    }

    public UUID findOrCreateByPhone(String phone) {
        return findOrCreate(() -> users.findByPhone(phone), new User(phone, null));
    }

    // email: already trimmed and lower-cased (AuthController), so one address is always one account
    public UUID findOrCreateByEmail(String email) {
        return findOrCreate(() -> users.findByEmail(email), new User(null, email));
    }

    private UUID findOrCreate(Supplier<Optional<User>> find, User newUser) {
        return find.get().map(User::getId).orElseGet(() -> {
            try {
                return users.saveAndFlush(newUser).getId();
            } catch (DataIntegrityViolationException e) {
                // Another request created this user a moment ago (unique phone / email); use theirs
                return find.get().orElseThrow().getId();
            }
        });
    }

    // Ratings summaries (and the ID badge) for everyone shown on a screen, in one query. rating_sum counts thumbs up (V7).
    public Map<UUID, Rating> ratings(Collection<UUID> ids) {
        Map<UUID, Rating> out = new HashMap<>();
        if (!ids.isEmpty()) {
            db.sql("select id, rating_count, rating_sum, verification = 'verified' as verified from users where id in (:ids)")
                    .param("ids", ids).query(RatingRow.class).list()
                    .forEach(r -> out.put(r.id(), rating(r.ratingCount(), r.ratingSum(), r.verified())));
        }
        return out;
    }

    static Rating rating(int count, int ups, boolean idVerified) {
        return new Rating(count, count < MIN_RATINGS ? null : (int) Math.round(100.0 * ups / count), idVerified);
    }

    public boolean womenOnlyAllowed(UUID id) {
        return db.sql("select ? in " + WOMEN_ONLY_USERS).param(id).query(Boolean.class).single();
    }

    // Who reviews reports (users.role, V1). Made by hand with SQL, see infra/README.md.
    public List<UUID> adminIds() {
        return db.sql("select id from users where role = 'admin'").query(UUID.class).list();
    }

    // Admin-only pages and endpoints check this on the server, on every request (Learning Notes 80)
    public boolean isAdmin(UUID id) {
        return db.sql("select exists (select 1 from users where id = ? and role = 'admin')").param(id).query(Boolean.class).single();
    }

    // For the verification module, inside its decision's transaction. gender is the one printed on the ID.
    public void markVerified(UUID id, String gender) {
        db.sql("update users set verification = 'verified', gender = ?::gender, verified_at = now() where id = ?")
                .params(gender, id).update();
    }

    // For the feedback module, inside its transaction: the counters move together with the new ratings row
    public void addRating(UUID userId, boolean up) {
        db.sql("update users set rating_count = rating_count + 1, rating_sum = rating_sum + ? where id = ?")
                .params(up ? 1 : 0, userId).update();
    }

    // For showing other travellers (trips module): first names only, all ids in ONE query (no N+1).
    public Map<UUID, String> firstNames(Collection<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        for (User user : users.findAllById(ids)) {
            names.put(user.getId(), user.getName() == null ? "Traveller" : user.getName().split(" ")[0]);
        }
        return names;
    }

    // Only for the booker's own group members, so they can pay their share (design 4.4)
    public String upiId(UUID id) {
        return users.findById(id).map(User::getUpiId).orElse(null);
    }

    // ---- Blocks (design 2.2: the users module owns the blocks table; Learning Notes 56.2) ----

    // Idempotent: blocking twice changes nothing
    public void block(UUID blocker, UUID blocked) {
        db.sql("insert into blocks (blocker_id, blocked_id) values (?, ?) on conflict do nothing").params(blocker, blocked).update();
    }

    // Did `me` block any of `others`, or did any of them block `me`? (Blocks hide people both ways.)
    public boolean blockedEitherWay(UUID me, Collection<UUID> others) {
        return !others.isEmpty() && db.sql("""
                        select count(*) from blocks
                        where (blocker_id = :me and blocked_id in (:others)) or (blocked_id = :me and blocker_id in (:others))""")
                .param("me", me).param("others", others).query(Integer.class).single() > 0;
    }

    public List<BlockView> blocks(UUID blocker) {
        List<BlockRow> rows = db.sql("select id, blocked_id from blocks where blocker_id = ? order by created_at desc")
                .param(blocker).query(BlockRow.class).list();
        Map<UUID, String> names = firstNames(rows.stream().map(BlockRow::blockedId).toList());
        return rows.stream().map(r -> new BlockView(r.id(), names.get(r.blockedId()))).toList();
    }

    // false if there was no such block of yours
    public boolean unblock(UUID blocker, UUID blockId) {
        return db.sql("delete from blocks where id = ? and blocker_id = ?").params(blockId, blocker).update() == 1;
    }

    Profile profile(UUID id) {
        return toProfile(users.findById(id).orElseThrow());
    }

    // null = leave unchanged; an empty upiId = remove it. No save() call: see Learning Notes 31.3 (dirty checking).
    @Transactional
    Profile updateProfile(UUID id, String name, String upiId) {
        User user = users.findById(id).orElseThrow();
        if (name != null) {
            user.changeName(name);
        }
        if (upiId != null) {
            user.changeUpiId(upiId.isEmpty() ? null : upiId.toLowerCase(Locale.ROOT));
        }
        return toProfile(user);
    }

    private Profile toProfile(User user) {
        return new Profile(user.getId(), user.getPhone(), user.getEmail(), user.getName(), user.getUpiId(), user.isAdmin(),
                womenOnlyAllowed(user.getId()));
    }

    // admin: shows the "Review ID checks" link; every admin endpoint checks the role again on the server.
    // womenOnlyAllowed: unlocks the women-only switch; creating the trip checks it again on the server.
    // phone / email: how this account logs in (at least one is set, V12); only ever shown to the user themselves
    record Profile(UUID id, @Schema(nullable = true) String phone, @Schema(nullable = true) String email,
                   @Schema(nullable = true) String name, @Schema(nullable = true) String upiId,
                   boolean admin, boolean womenOnlyAllowed) {}
}
