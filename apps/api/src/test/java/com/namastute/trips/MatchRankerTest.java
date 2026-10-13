package com.namastute.trips;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

// Plain unit tests: no Spring, no database. MatchRanker is a pure function, so each test is just inputs → output.
class MatchRankerTest {

    static final Point T3 = Geo.point(28.5555, 77.0865);
    static final Instant EXIT = Instant.parse("2026-10-14T16:40:00Z");   // 22:10 IST

    @Test
    void riderFurtherDownTheSameRoadIsAGoodMatch() {
        TripRequest me = trip(28.4936, 77.0930, 0);           // DLF Phase 3, 9.7 km solo
        TripRequest asha = trip(28.4683, 77.0628, 10);        // Sector 29, 14.0 km solo, past my stop

        List<MatchRanker.Ranked> ranked = MatchRanker.rank(T3, me, List.of(asha));

        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).detourKm()).as("route 15.4 − longest solo 14.0").isCloseTo(1.4, within(0.1));
        assertThat(ranked.get(0).score()).isCloseTo(1.4 + 0.2 * 10, within(0.1));
    }

    @Test
    void sameDestinationHasNoDetour() {
        TripRequest me = trip(28.4936, 77.0930, 0);
        TripRequest twin = trip(28.4936, 77.0930, -15);

        MatchRanker.Ranked match = MatchRanker.rank(T3, me, List.of(twin)).get(0);

        assertThat(match.detourKm()).isCloseTo(0, within(1e-9));
        assertThat(match.score()).as("only the 15 minutes count").isCloseTo(3.0, within(1e-9));
    }

    @Test
    void oppositeDirectionIsRejected() {
        TripRequest me = trip(28.4936, 77.0930, 0);           // DLF Phase 3 (south)
        TripRequest ravi = trip(28.5523, 77.0583, 0);         // Dwarka Sector 21 (west): I'd ride 4.5 km extra, limit 3

        assertThat(MatchRanker.rank(T3, me, List.of(ravi))).isEmpty();
    }

    // Short trips: 25 % would allow only ~1.4 km, but the 3 km floor applies
    @Test
    void shortTripsGetAtLeast3KmAllowance() {
        TripRequest me = at(3, 0);

        assertThat(MatchRanker.rank(T3, me, List.of(at(3, 2.5)))).as("detour ≈ 2.2 km").hasSize(1);
        assertThat(MatchRanker.rank(T3, me, List.of(at(3, 6)))).as("detour ≈ 3.2 km").isEmpty();
    }

    // Long trips: 25 % of ~43 km ≈ 10.9 km, far more than the 3 km floor
    @Test
    void longTripsGet25PercentAllowance() {
        TripRequest me = at(30, 0);

        assertThat(MatchRanker.rank(T3, me, List.of(at(30, 8)))).as("detour ≈ 9.7 km").hasSize(1);
        assertThat(MatchRanker.rank(T3, me, List.of(at(30, 12)))).as("detour ≈ 13.6 km, limit ≈ 11.3").isEmpty();
    }

    @Test
    void everyoneMustFitInOneCar() {
        TripRequest me = trip(28.4936, 77.0930, 0, 2, 2);

        assertThat(MatchRanker.rank(T3, me, List.of(trip(28.4936, 77.0930, 0, 2, 2)))).as("4 seats, 4 bags").hasSize(1);
        assertThat(MatchRanker.rank(T3, me, List.of(trip(28.4936, 77.0930, 0, 3, 0)))).as("5 seats").isEmpty();
        assertThat(MatchRanker.rank(T3, me, List.of(trip(28.4936, 77.0930, 0, 1, 3)))).as("5 bags").isEmpty();
    }

    @Test
    void lowestScoreFirstAndOnlyTheTop5() {
        TripRequest me = trip(28.4936, 77.0930, 0);
        TripRequest neha = trip(28.4950, 77.0895, 25);        // Cyber City: tiny detour, but 25 min apart → 5.3
        TripRequest asha = trip(28.4683, 77.0628, 10);        // 1.4 km detour, 10 min apart → 3.4
        List<TripRequest> sameSpot = List.of(5, -20, 1, 18, 7).stream().map(m -> trip(28.4936, 77.0930, m)).toList();

        List<MatchRanker.Ranked> ranked = MatchRanker.rank(T3, me,
                List.of(neha, sameSpot.get(0), asha, sameSpot.get(1), sameSpot.get(2), sameSpot.get(3), sameSpot.get(4)));

        // scores: 1 min → 0.2, 5 → 1.0, 7 → 1.4, asha 3.4, 18 → 3.6, 20 → 4.0, neha 5.3
        assertThat(ranked).extracting(MatchRanker.Ranked::trip)
                .containsExactly(sameSpot.get(2), sameSpot.get(0), sameSpot.get(4), asha, sameSpot.get(3));
    }

    @Test
    void noCandidatesNoMatches() {
        assertThat(MatchRanker.rank(T3, trip(28.4936, 77.0930, 0), List.of())).isEmpty();
    }

    private static TripRequest trip(double lat, double lng, int minutesAfter) {
        return trip(lat, lng, minutesAfter, 1, 1);
    }

    private static TripRequest trip(double lat, double lng, int minutesAfter, int seats, int bags) {
        return new TripRequest(UUID.randomUUID(), "DEL-T3", null, EXIT.plus(Duration.ofMinutes(minutesAfter)),
                Geo.point(lat, lng), "Somewhere", 0, (short) seats, (short) bags, false);
    }

    // A destination kmSouth and kmEast of the T3 pickup (straight line), leaving at the same time as me
    private static TripRequest at(double kmSouth, double kmEast) {
        double kmPerDegreeLat = 111.19;
        double kmPerDegreeLng = kmPerDegreeLat * Math.cos(Math.toRadians(Geo.lat(T3)));
        return trip(Geo.lat(T3) - kmSouth / kmPerDegreeLat, Geo.lng(T3) + kmEast / kmPerDegreeLng, 0);
    }
}
