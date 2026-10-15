package com.namastute.users;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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

    private final UserRepository users;
    private final JdbcClient db;

    UserService(UserRepository users, JdbcClient db) {
        this.users = users;
        this.db = db;
    }

    public UUID findOrCreateByPhone(String phone) {
        return users.findByPhone(phone)
                .map(User::getId)
                .orElseGet(() -> create(phone));
    }

    private UUID create(String phone) {
        try {
            return users.saveAndFlush(new User(phone)).getId();
        } catch (DataIntegrityViolationException e) {
            // Another request created this phone's user a moment ago (unique constraint on phone); use theirs
            return users.findByPhone(phone).orElseThrow().getId();
        }
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

    private static Profile toProfile(User user) {
        return new Profile(user.getId(), user.getPhone(), user.getName(), user.getUpiId());
    }

    record Profile(UUID id, String phone, @Schema(nullable = true) String name, @Schema(nullable = true) String upiId) {}
}
