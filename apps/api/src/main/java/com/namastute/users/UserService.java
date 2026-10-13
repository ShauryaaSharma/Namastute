package com.namastute.users;

import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The users module's public API: other modules (auth) call these methods, never the repository directly
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

    record Profile(UUID id, String phone, String name, String upiId) {}
}
