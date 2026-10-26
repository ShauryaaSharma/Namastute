package com.namastute.auth;

import com.namastute.shared.ApiException;
import com.namastute.users.UserService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")   // every path below starts with this
class AuthController {

    static final String INDIAN_MOBILE = "^\\+91[6-9]\\d{9}$";
    static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";   // something@something.something; the code proves the rest
    static final String REFRESH_COOKIE = "refresh_token";

    // Log in by phone number or by email address (Learning Notes 88): exactly one of the two
    record OtpRequest(
            @Schema(nullable = true) @Pattern(regexp = INDIAN_MOBILE, message = "must be an Indian mobile number like +919876543210")
            String phone,
            @Schema(nullable = true) @Size(max = 254) @Pattern(regexp = EMAIL, message = "must be an email address like you@example.com")
            String email) {}

    record VerifyRequest(
            @Schema(nullable = true) @Pattern(regexp = INDIAN_MOBILE, message = "must be an Indian mobile number like +919876543210")
            String phone,
            @Schema(nullable = true) @Size(max = 254) @Pattern(regexp = EMAIL, message = "must be an email address like you@example.com")
            String email,
            @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be 6 digits")
            String code) {}

    record TokenResponse(String accessToken, long expiresIn) {}

    private final OtpService otp;
    private final UserService users;
    private final AccessTokens accessTokens;
    private final RefreshTokens refreshTokens;

    AuthController(OtpService otp, UserService users, AccessTokens accessTokens, RefreshTokens refreshTokens) {
        this.otp = otp;
        this.users = users;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
    }

    @PostMapping("/otp")
    ResponseEntity<Void> requestOtp(@Valid @RequestBody OtpRequest req, HttpServletRequest http) {
        // The real client IP: behind Caddy, prod reads it from X-Forwarded-For (server.forward-headers-strategy, Learning Notes 54.5)
        otp.requestOtp(destination(req.phone(), req.email()), http.getRemoteAddr());
        return ResponseEntity.noContent().build();   // 204: done, nothing to send back
    }

    @PostMapping("/verify")
    ResponseEntity<TokenResponse> verify(@Valid @RequestBody VerifyRequest req) {
        String to = destination(req.phone(), req.email());
        otp.verify(to, req.code());
        UUID userId = req.phone() != null ? users.findOrCreateByPhone(to) : users.findOrCreateByEmail(to);
        return tokens(userId, refreshTokens.create(userId));
    }

    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
        if (token == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_INVALID", "Session expired, please log in again");
        }
        RefreshTokens.Rotated rotated = refreshTokens.rotate(token);
        return tokens(rotated.userId(), rotated.token());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
        if (token != null) {
            refreshTokens.revoke(token);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())   // max-age 0 = browser deletes it
                .build();
    }

    // Exactly one way to reach the person. Emails are lower-cased, so "Asha@Gmail.com" is the same account as "asha@gmail.com".
    private static String destination(String phone, String email) {
        if ((phone == null) == (email == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Send a phone number or an email address");
        }
        return phone != null ? phone : email.toLowerCase(Locale.ROOT);
    }

    // 200 with a new access token in the body, and the refresh token in an HttpOnly cookie
    private ResponseEntity<TokenResponse> tokens(UUID userId, String refreshToken) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken, RefreshTokens.LIFETIME).toString())
                .body(new TokenResponse(accessTokens.issue(userId), AccessTokens.LIFETIME.toSeconds()));
    }

    private static ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)          // JavaScript can't read it, so XSS can't steal it
                .secure(true)            // only sent over HTTPS (browsers also allow it on http://localhost)
                .sameSite("Lax")         // not attached to POSTs started by other websites (blocks CSRF)
                .path("/api/v1/auth")    // only sent to the auth endpoints, not every API call
                .maxAge(maxAge)
                .build();
    }
}
