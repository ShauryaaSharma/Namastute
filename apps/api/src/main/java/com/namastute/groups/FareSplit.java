package com.namastute.groups;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// Design 3.8: share = fare × your solo km ÷ everyone's solo km. A pure function (Learning Notes 50.3).
final class FareSplit {

    private FareSplit() {}

    // Everyone except the booker is rounded to whole rupees (HALF_UP); the booker pays what's left,
    // so the shares always add up to the fare exactly. soloKm: member → solo km (the booker included).
    static Map<UUID, BigDecimal> split(BigDecimal fare, Map<UUID, Double> soloKm, UUID booker) {
        Map<UUID, BigDecimal> shares = shares(fare, soloKm, booker, RoundingMode.HALF_UP);
        if (shares.get(booker).signum() < 0) {
            // Only when the booker's own share is tiny (≈ ₹1) and the others all rounded up: round them down
            // instead, so the booker never "owes" a negative amount
            shares = shares(fare, soloKm, booker, RoundingMode.FLOOR);
        }
        return shares;
    }

    private static Map<UUID, BigDecimal> shares(BigDecimal fare, Map<UUID, Double> soloKm, UUID booker, RoundingMode mode) {
        // The km are only weights; the money itself stays BigDecimal throughout
        BigDecimal totalKm = BigDecimal.valueOf(soloKm.values().stream().mapToDouble(Double::doubleValue).sum());
        Map<UUID, BigDecimal> shares = new LinkedHashMap<>();
        BigDecimal others = BigDecimal.ZERO;
        for (Map.Entry<UUID, Double> member : soloKm.entrySet()) {
            if (!member.getKey().equals(booker)) {
                BigDecimal share = fare.multiply(BigDecimal.valueOf(member.getValue())).divide(totalKm, 0, mode);
                shares.put(member.getKey(), share);
                others = others.add(share);
            }
        }
        shares.put(booker, fare.subtract(others));
        return shares;
    }
}
