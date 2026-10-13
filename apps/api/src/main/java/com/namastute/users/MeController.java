package com.namastute.users;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    // Starts with a letter (any language); letters, combining marks (Indian-script vowel signs like ी), spaces, . ' - inside;
    // ends with a letter, mark or dot
    static final String NAME = "^\\p{L}([\\p{L}\\p{M} .'-]*[\\p{L}\\p{M}.])?$";
    // UPI ID: handle@bank, e.g. keshav.d@okaxis. Empty string = remove it.
    static final String UPI_ID_OR_EMPTY = "^$|^[a-zA-Z0-9._-]{2,256}@[a-zA-Z]{2,64}$";

    // Both fields optional: a field that is left out (null) is not changed
    record UpdateMe(
            @Size(min = 2, max = 60, message = "must be 2 to 60 characters")
            @Pattern(regexp = NAME, message = "may only contain letters, spaces, dots, apostrophes and hyphens")
            String name,
            @Pattern(regexp = UPI_ID_OR_EMPTY, message = "must look like yourname@bank")
            String upiId) {}

    private final UserService users;

    MeController(UserService users) {
        this.users = users;
    }

    // Protected endpoint: Spring Security has already checked the token; the JWT's subject is the user's id
    @GetMapping("/api/v1/me")
    UserService.Profile me(@AuthenticationPrincipal Jwt jwt) {
        return users.profile(UUID.fromString(jwt.getSubject()));
    }

    // PATCH = change part of a resource. Users can only ever edit themselves: the id comes from the token, not the URL.
    @PatchMapping("/api/v1/me")
    UserService.Profile update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateMe req) {
        return users.updateProfile(UUID.fromString(jwt.getSubject()), req.name(), req.upiId());
    }
}
