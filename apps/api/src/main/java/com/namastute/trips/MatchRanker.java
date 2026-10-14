package com.namastute.trips;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import org.locationtech.jts.geom.Point;

// Stage 2 of matching (stage 1: TripRepository.matchCandidates for travellers, OpenGroups for groups).
// A pure function: no Spring, no database, no clock. The same inputs always give the same answer.
final class MatchRanker {

    static final int TOP = 5;
    static final int CAR_SEATS = 4;               // a sedan (design 3.3)
    static final int CAR_BAGS = 4;
    static final double MIN_ALLOWANCE_KM = 3;     // nobody rides more than max(3 km, 25 %) beyond their solo trip
    static final double ALLOWANCE = 0.25;
    static final double KM_PER_MINUTE = 0.2;      // 1 km of detour is as bad as 5 minutes of waiting

    // riders = the people already sharing: one for a lone traveller, 2–3 for an open group
    record Ranked(List<TripRequest> riders, double detourKm, double score) {}

    private MatchRanker() {}

    // Best candidates for `mine` to join, lowest score first
    static List<Ranked> rank(Point pickup, TripRequest mine, List<List<TripRequest>> candidates) {
        List<Ranked> ok = new ArrayList<>();
        for (List<TripRequest> riders : candidates) {
            List<TripRequest> everyone = new ArrayList<>(riders);
            everyone.add(mine);
            if (everyone.stream().mapToInt(TripRequest::getSeats).sum() > CAR_SEATS
                    || everyone.stream().mapToInt(TripRequest::getBags).sum() > CAR_BAGS) {
                continue;   // doesn't fit in the car
            }
            OptionalDouble detour = fairDetour(pickup, everyone);
            if (detour.isEmpty()) {
                continue;   // someone would ride too far out of their way
            }
            // A group meets when its last member is out (its meet time); a traveller's time is their own exit
            Instant theirTime = riders.stream().map(TripRequest::getExitAt).max(Comparator.naturalOrder()).orElseThrow();
            long minutes = Math.abs(Duration.between(mine.getExitAt(), theirTime).toMinutes());
            ok.add(new Ranked(riders, detour.getAsDouble(), detour.getAsDouble() + KM_PER_MINUTE * minutes));
        }
        return ok.stream().sorted(Comparator.comparingDouble(Ranked::score)).limit(TOP).toList();
    }

    // Greedy route: from the pickup, always drive to the nearest remaining drop (Learning Notes 42.3).
    // Returns the detour (route − longest solo trip), or empty if any rider would ride more than their allowance.
    private static OptionalDouble fairDetour(Point pickup, List<TripRequest> everyone) {
        List<TripRequest> left = new ArrayList<>(everyone);
        Point at = pickup;
        double routeKm = 0;
        double longestSolo = 0;
        while (!left.isEmpty()) {
            Point from = at;
            TripRequest next = left.stream().min(Comparator.comparingDouble(t -> roadKm(from, t.getDest()))).orElseThrow();
            routeKm += roadKm(at, next.getDest());   // this rider gets out after routeKm
            double solo = roadKm(pickup, next.getDest());
            if (routeKm - solo > Math.max(MIN_ALLOWANCE_KM, ALLOWANCE * solo)) {
                return OptionalDouble.empty();
            }
            longestSolo = Math.max(longestSolo, solo);
            at = next.getDest();
            left.remove(next);
        }
        return OptionalDouble.of(routeKm - longestSolo);
    }

    private static double roadKm(Point a, Point b) {
        return Geo.distanceKm(a, b) * Geo.ROAD_FACTOR;
    }
}
