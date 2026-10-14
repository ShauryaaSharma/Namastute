package com.namastute.trips;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
class TripController {

    record TerminalView(String id, String name, double lat, double lng, @Schema(nullable = true) String pickupNote) {}

    record CreateTripRequest(
            @NotBlank String terminalId,
            @Pattern(regexp = "^$|^[A-Za-z0-9]{2} ?\\d{1,4}$", message = "must look like AI 865") @Schema(nullable = true) String flightNo,
            @NotNull Instant exitAt,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double destLat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double destLng,
            @NotBlank @Size(max = 120) String destArea,
            @Min(1) @Max(3) int seats,
            @Min(0) @Max(6) int bags,
            boolean womenOnly) {}

    private final TripService trips;

    TripController(TripService trips) {
        this.trips = trips;
    }

    @GetMapping("/api/v1/terminals")
    List<TerminalView> terminals() {
        return trips.terminals().stream()
                .map(t -> new TerminalView(t.getId(), t.getName(), Geo.lat(t.getPickup()), Geo.lng(t.getPickup()), t.getPickupNote()))
                .toList();
    }

    @PostMapping("/api/v1/trips")
    ResponseEntity<TripService.TripView> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateTripRequest req) {
        TripService.TripView trip = trips.create(userId(jwt), new TripService.CreateTrip(req.terminalId(), req.flightNo(),
                req.exitAt(), req.destLat(), req.destLng(), req.destArea(), req.seats(), req.bags(), req.womenOnly()));
        return ResponseEntity.status(HttpStatus.CREATED).body(trip);   // 201: a new thing was created
    }

    // 200 with your active trip, or 204 if you don't have one
    @GetMapping("/api/v1/trips/current")
    ResponseEntity<TripService.TripView> current(@AuthenticationPrincipal Jwt jwt) {
        return trips.current(userId(jwt))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // Up to 5 people to share a cab with, best first; 404 unless it's your own active trip
    @GetMapping("/api/v1/trips/{id}/matches")
    List<TripService.MatchView> matches(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return trips.matches(userId(jwt), id);
    }

    @DeleteMapping("/api/v1/trips/{id}")
    ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        trips.cancel(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
