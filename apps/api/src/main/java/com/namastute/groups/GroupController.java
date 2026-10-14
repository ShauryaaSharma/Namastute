package com.namastute.groups;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class GroupController {

    record AskRequest(@NotNull UUID toTripId) {}

    record LeaveRequest(@Size(max = 300) String reason) {}

    record Created(UUID id) {}

    // Money arrives as an exact decimal (BigDecimal), never a double (Learning Notes 50.2)
    record FareRequest(@NotNull @DecimalMin("50") @DecimalMax("10000") @Digits(integer = 5, fraction = 2) BigDecimal total) {}

    private final GroupService groups;

    GroupController(GroupService groups) {
        this.groups = groups;
    }

    // Ask a traveller (from your matches) to share a cab
    @PostMapping("/api/v1/requests")
    ResponseEntity<Created> ask(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AskRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new Created(groups.ask(userId(jwt), req.toTripId())));
    }

    @GetMapping("/api/v1/requests")
    GroupService.Requests requests(@AuthenticationPrincipal Jwt jwt) {
        return groups.requests(userId(jwt));
    }

    @PostMapping("/api/v1/requests/{id}/accept")
    ResponseEntity<Void> accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        groups.accept(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/requests/{id}/decline")
    ResponseEntity<Void> decline(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        groups.decline(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    // 200 with your group, or 204 if you're not in one
    @GetMapping("/api/v1/groups/current")
    ResponseEntity<GroupService.GroupView> current(@AuthenticationPrincipal Jwt jwt) {
        return groups.current(userId(jwt))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/api/v1/groups/{id}/lock")
    ResponseEntity<Void> lock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        groups.lock(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    // The body is optional: a reason is only required when leaving a locked group
    @PostMapping("/api/v1/groups/{id}/leave")
    ResponseEntity<Void> leave(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                               @Valid @RequestBody(required = false) LeaveRequest req) {
        groups.leave(userId(jwt), id, req == null ? null : req.reason());
        return ResponseEntity.noContent().build();
    }

    // "I'm here" at the pickup point
    @PostMapping("/api/v1/groups/{id}/here")
    ResponseEntity<Void> here(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        groups.here(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    // Booker only: everyone's share for this fare, before confirming. Saves nothing.
    @PostMapping("/api/v1/groups/{id}/fare/preview")
    List<GroupService.ShareView> farePreview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody FareRequest req) {
        return groups.farePreview(userId(jwt), id, req.total());
    }

    @PostMapping("/api/v1/groups/{id}/fare")
    ResponseEntity<Void> enterFare(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody FareRequest req) {
        groups.enterFare(userId(jwt), id, req.total());
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
