package com.namastute.verification;

import com.namastute.verification.ReviewService.PendingView;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Admins only (Learning Notes 80); ReviewService checks the role on every call
@RestController
class ReviewController {

    record ApproveRequest(
            @NotNull @Pattern(regexp = "female|male|other", message = "must be female, male or other")
            @Schema(allowableValues = {"female", "male", "other"}) String gender,
            @NotNull @Pattern(regexp = "[A-Za-z0-9]{4}", message = "must be the last 4 letters or digits of the document number")
            String docLast4) {}

    record RejectRequest(@NotNull @Size(min = 3, max = 200) String reason) {}

    private final ReviewService review;

    ReviewController(ReviewService review) {
        this.review = review;
    }

    @GetMapping("/api/v1/admin/verifications")
    List<PendingView> pending(@AuthenticationPrincipal Jwt jwt) {
        return review.pending(userId(jwt));
    }

    // which = "id" or "selfie". no-store: the browser must not keep a copy of anyone's ID
    @GetMapping(value = "/api/v1/admin/verifications/{id}/photos/{which}", produces = MediaType.IMAGE_JPEG_VALUE)
    ResponseEntity<byte[]> photo(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable String which) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(review.photo(userId(jwt), id, which));
    }

    @PostMapping("/api/v1/admin/verifications/{id}/approve")
    ResponseEntity<Void> approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ApproveRequest req) {
        review.approve(userId(jwt), id, req.gender(), req.docLast4());   // committed when it returns
        review.deletePhotos(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/admin/verifications/{id}/reject")
    ResponseEntity<Void> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody RejectRequest req) {
        review.reject(userId(jwt), id, req.reason());
        review.deletePhotos(id);
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
