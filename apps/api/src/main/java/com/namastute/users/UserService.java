package com.namastute.users;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

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
        User user = users.findById(id).orElseThrow();
        return new Profile(user.getId(), user.getPhone(), user.getName());
    }

    record Profile(UUID id, String phone, String name) {}
}
