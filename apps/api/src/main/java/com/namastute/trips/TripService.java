package com.namastute.trips;

import com.namastute.shared.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.locationtech.jts.geom.Point;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
class TripService {

    static final Duration EXIT_GRACE = Duration.ofMinutes(5);      // "I'm outside right now" is fine
    static final Duration MAX_AHEAD = Duration.ofHours(24);
    static final Duration EXPIRES_AFTER = Duration.ofMinutes(60);   // design 3.7: a trip expires at exit_at + 60 min
    static final double MAX_DISTANCE_KM = 100;                      // straight line; beyond that it isn't an airport cab ride
    private static final List<TripRequest.Status> ACTIVE = List.of(TripRequest.Status.searching, TripRequest.Status.grouped);

    record CreateTrip(String terminalId, String flightNo, Instant exitAt, double destLat, double destLng,
                      String destArea, int seats, int bags, boolean womenOnly) {}

    record TripView(UUID id, String terminalId, String terminalName, String flightNo, Instant exitAt,
                    String destArea, double destLat, double destLng, int seats, int bags,
                    boolean womenOnly, String status, double soloKm) {}

    private final TripRepository trips;
    private final TerminalRepository terminals;

    TripService(TripRepository trips, TerminalRepository terminals) {
        this.trips = trips;
        this.terminals = terminals;
    }

    TripView create(UUID userId, CreateTrip req) {
        Terminal terminal = terminals.findById(req.terminalId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_TERMINAL", "Pick one of the IGI terminals"));

        Instant now = Instant.now();
        if (req.exitAt().isBefore(now.minus(EXIT_GRACE)) || req.exitAt().isAfter(now.plus(MAX_AHEAD))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXIT_TIME_OUT_OF_RANGE",
                    "Exit time must be between now and 24 hours from now");
        }
        // shortcut: refused for everyone until ID verification exists; then allow verified women (design 4.3)
        if (req.womenOnly()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WOMEN_ONLY_NEEDS_VERIFICATION",
                    "Women-only trips need ID verification, which is coming soon");
        }

        Point dest = Geo.point(req.destLat(), req.destLng());
        double straightKm = Geo.distanceKm(terminal.getPickup(), dest);
        if (straightKm > MAX_DISTANCE_KM) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DESTINATION_TOO_FAR",
                    "That destination is more than 100 km from the airport");
        }

        // A finished-but-never-expired trip would block the new one through the one-active-trip index
        trips.expireStale(userId, now.minus(EXPIRES_AFTER));
        try {
            TripRequest trip = trips.saveAndFlush(new TripRequest(userId, terminal.getId(), normalizeFlight(req.flightNo()),
                    req.exitAt(), dest, req.destArea().strip(), (float) (straightKm * Geo.ROAD_FACTOR),
                    (short) req.seats(), (short) req.bags(), false));
            return view(trip, terminal);
        } catch (DataIntegrityViolationException e) {
            // The database's unique index trip_one_active decided: this user already has an active trip
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_TRIP_EXISTS",
                    "You already have an active trip. Cancel it to plan a new one.");
        }
    }

    Optional<TripView> current(UUID userId) {
        return trips.findFirstByUserIdAndStatusIn(userId, ACTIVE)
                .filter(t -> t.getExitAt().isAfter(Instant.now().minus(EXPIRES_AFTER)))
                .map(t -> view(t, terminals.findById(t.getTerminalId()).orElseThrow()));
    }

    void cancel(UUID userId, UUID tripId) {
        if (trips.cancel(tripId, userId) == 0) {
            // Same answer whether it doesn't exist or belongs to someone else: we don't reveal other users' trips
            throw new ApiException(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "No active trip to cancel");
        }
    }

    List<Terminal> terminals() {
        return terminals.findAll();
    }

    // "ai 865" / "AI865" → "AI865"; empty → null
    private static String normalizeFlight(String flightNo) {
        if (flightNo == null || flightNo.isBlank()) {
            return null;
        }
        return flightNo.replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private static TripView view(TripRequest t, Terminal terminal) {
        return new TripView(t.getId(), terminal.getId(), terminal.getName(), t.getFlightNo(), t.getExitAt(),
                t.getDestArea(), Geo.lat(t.getDest()), Geo.lng(t.getDest()), t.getSeats(), t.getBags(),
                t.isWomenOnly(), t.getStatus().name(), Math.round(t.getSoloKm() * 10) / 10.0);
    }
}
