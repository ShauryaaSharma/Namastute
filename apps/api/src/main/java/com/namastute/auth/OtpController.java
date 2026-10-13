package com.namastute.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")   // every path below starts with this
class OtpController {

    static final String INDIAN_MOBILE = "^\\+91[6-9]\\d{9}$";

    record OtpRequest(
            @NotBlank @Pattern(regexp = INDIAN_MOBILE, message = "must be an Indian mobile number like +919876543210")
            String phone) {}

    record VerifyRequest(
            @NotBlank @Pattern(regexp = INDIAN_MOBILE, message = "must be an Indian mobile number like +919876543210")
            String phone,
            @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be 6 digits")
            String code) {}

    private final OtpService otp;

    OtpController(OtpService otp) {
        this.otp = otp;
    }

    @PostMapping("/otp")
    ResponseEntity<Void> requestOtp(@Valid @RequestBody OtpRequest req, HttpServletRequest http) {
        // shortcut: getRemoteAddr() is the direct caller; behind a proxy (Caddy, Phase 2) read the forwarded IP instead
        otp.requestOtp(req.phone(), http.getRemoteAddr());
        return ResponseEntity.noContent().build();   // 204: done, nothing to send back
    }

    @PostMapping("/verify")
    ResponseEntity<Void> verify(@Valid @RequestBody VerifyRequest req) {
        otp.verify(req.phone(), req.code());
        // shortcut: returns 204 for now; feat/auth-tokens makes this return the user's tokens
        return ResponseEntity.noContent().build();
    }
}