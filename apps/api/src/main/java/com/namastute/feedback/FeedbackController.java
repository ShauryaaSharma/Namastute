package com.namastute.feedback;

import com.namastute.users.UserService.BlockView;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class FeedbackController {

    record BlockRequest(@NotNull UUID tripId) {}

    record ReportRequest(
            @NotNull UUID tripId,
            @NotNull @Pattern(regexp = "unsafe|harassment|no_show|fake_profile|other", message = "must be one of the listed reasons")
            @Schema(allowableValues = {"unsafe", "harassment", "no_show", "fake_profile", "other"}) String category,
            @Size(max = 500) @Schema(nullable = true) String details) {}

    private final FeedbackService feedback;

    FeedbackController(FeedbackService feedback) {
        this.feedback = feedback;
    }

    @PostMapping("/api/v1/blocks")
    ResponseEntity<Void> block(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BlockRequest req) {
        feedback.block(userId(jwt), req.tripId());
        return ResponseEntity.noContent().build();
    }

    // Your "Blocked people" list: a handle to unblock and a first name each
    @GetMapping("/api/v1/blocks")
    List<BlockView> blocks(@AuthenticationPrincipal Jwt jwt) {
        return feedback.blocks(userId(jwt));
    }

    @DeleteMapping("/api/v1/blocks/{id}")
    ResponseEntity<Void> unblock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        feedback.unblock(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/reports")
    ResponseEntity<Void> report(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReportRequest req) {
        feedback.report(userId(jwt), req.tripId(), req.category(), req.details());
        return ResponseEntity.noContent().build();   // nothing to send back: the reporter only needs "thanks"
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
