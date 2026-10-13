package com.namastute.trips;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.locationtech.jts.geom.Point;

// Stage 2 of matching (stage 1 is the SQL in TripRepository.matchCandidates).
// A pure function: no Spring, no database, no clock. The same inputs always give the same answer.
final class MatchRanker {

    static final int TOP = 5;
    static final int CAR_SEATS = 4;               // a sedan (design 3.3)
    static final int CAR_BAGS = 4;
    static final double MIN_ALLOWANCE_KM = 3;     // nobody rides more than max(3 km, 25 %) beyond their solo trip
    static final double ALLOWANCE = 0.25;
    static final double KM_PER_MINUTE = 0.2;      // 1 km of detour is as bad as 5 minutes of waiting

    record Ranked(TripRequest trip, double detourKm, double score) {}

    private MatchRanker() {}

    // Best candidates for sharing one cab with `mine`, lowest score first. Two riders per cab for now.
    static List<Ranked> rank(Point pickup, TripRequest mine, List<TripRequest> candidates) {
        double mySolo = roadKm(pickup, mine.getDest());
        List<Ranked> ok = new ArrayList<>();
        for (TripRequest other : candidates) {
            if (mine.getSeats() + other.getSeats() > CAR_SEATS || mine.getBags() + other.getBags() > CAR_BAGS) {
                continue;   // doesn't fit in the car
            }
            double theirSolo = roadKm(pickup, other.getDest());
            double longest = Math.max(mySolo, theirSolo);
            // Greedy route: drop the nearer person first. They ride exactly their solo trip;
            // the farther one rides the whole route, so the detour is all theirs.
            double route = Math.min(mySolo, theirSolo) + roadKm(mine.getDest(), other.getDest());
            double detour = route - longest;
            if (detour > Math.max(MIN_ALLOWANCE_KM, ALLOWANCE * longest)) {
                continue;   // too far out of the way for the last rider
            }
            long minutes = Math.abs(Duration.between(mine.getExitAt(), other.getExitAt()).toMinutes());
            ok.add(new Ranked(other, detour, detour + KM_PER_MINUTE * minutes));
        }
        return ok.stream().sorted(Comparator.comparingDouble(Ranked::score)).limit(TOP).toList();
    }

    private static double roadKm(Point a, Point b) {
        return Geo.distanceKm(a, b) * Geo.ROAD_FACTOR;
    }
}
