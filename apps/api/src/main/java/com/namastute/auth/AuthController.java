package com.namastute.auth;

import com.namastute.shared.ApiException;
import com.namastute.users.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
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
    static final String REFRESH_COOKIE = "refresh_token";

    record OtpRequest(
            @NotBlank @Pattern(regexp = INDIAN_MOBILE, message = "must be an Indian mobile number like +919876543210")
            String phone) {}

    record VerifyRequest(
            @NotBlank @Pattern(regexp = INDIAN_MOBILE, message = "must be an Indian mobile number like +919876543210")
            String phone,
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
        otp.requestOtp(req.phone(), http.getRemoteAddr());
        return ResponseEntity.noContent().build();   // 204: done, nothing to send back
    }

    @PostMapping("/verify")
    ResponseEntity<TokenResponse> verify(@Valid @RequestBody VerifyRequest req) {
        otp.verify(req.phone(), req.code());
        UUID userId = users.findOrCreateByPhone(req.phone());
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
