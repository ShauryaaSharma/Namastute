package com.namastute.users;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    private final UserService users;

    MeController(UserService users) {
        this.users = users;
    }

    // Protected endpoint: Spring Security has already checked the token; the JWT's subject is the user's id
    @GetMapping("/api/v1/me")
    UserService.Profile me(@AuthenticationPrincipal Jwt jwt) {
        return users.profile(UUID.fromString(jwt.getSubject()));
    }
}
