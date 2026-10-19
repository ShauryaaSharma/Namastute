package com.namastute.verification;

import com.namastute.verification.VerificationService.VerificationView;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
class VerificationController {

    private final VerificationService verification;

    VerificationController(VerificationService verification) {
        this.verification = verification;
    }

    // Where your check stands, and the gesture for your next selfie
    @GetMapping("/api/v1/verification")
    VerificationView current(@AuthenticationPrincipal Jwt jwt) {
        return verification.current(userId(jwt));
    }

    // One multipart request: the fields and both photos. Every part is optional here so that a missing one gets our own
    // clear error message from the service, not Spring's generic one.
    @PostMapping(value = "/api/v1/verification", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<VerificationView> submit(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam(required = false) String docType,
                                            @RequestParam(defaultValue = "false") boolean consent,
                                            @RequestPart(required = false) MultipartFile idPhoto,
                                            @RequestPart(required = false) MultipartFile selfie) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(verification.submit(userId(jwt), docType, consent, idPhoto, selfie));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
