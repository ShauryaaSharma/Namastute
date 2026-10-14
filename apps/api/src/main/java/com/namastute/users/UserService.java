package com.namastute.users;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The users module's public API: other modules (auth, trips) call these methods, never the repository directly
@Service
public class UserService {

    private final UserRepository users;

    UserService(UserRepository users) {
        this.users = users;
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
