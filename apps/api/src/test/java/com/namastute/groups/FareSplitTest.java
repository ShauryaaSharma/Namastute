package com.namastute.groups;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Plain unit tests: no Spring, no database
class FareSplitTest {

    static final UUID ASHA = UUID.randomUUID(), KESHAV = UUID.randomUUID(), NEHA = UUID.randomUUID();

    @Test
    void furthestPaysMostAndTheBookerTakesTheRest() {
        Map<UUID, BigDecimal> shares = FareSplit.split(rupees("600"), km(ASHA, 14.0, KESHAV, 9.7), KESHAV);

        assertThat(shares.get(ASHA)).isEqualByComparingTo("354");     // 600 × 14.0 / 23.7 = 354.43
        assertThat(shares.get(KESHAV)).isEqualByComparingTo("246");   // 600 − 354
    }

    @Test
    void sharesAlwaysAddUpToTheFare() {
        // Equal trips: 1000 / 3 = 333.33 each; rounded the others pay 333, the booker 334
        Map<UUID, BigDecimal> shares = FareSplit.split(rupees("1000"), km(ASHA, 10.0, KESHAV, 10.0, NEHA, 10.0), NEHA);

        assertThat(shares.get(ASHA)).isEqualByComparingTo("333");
        assertThat(shares.get(KESHAV)).isEqualByComparingTo("333");
        assertThat(shares.get(NEHA)).isEqualByComparingTo("334");
        assertThat(total(shares)).isEqualByComparingTo("1000");
    }

    @Test
    void halfAWholeRupeeRoundsUp() {
        // 399 × 1/2 = 199.5 → 200 for Asha; the booker pays 199
        Map<UUID, BigDecimal> shares = FareSplit.split(rupees("399"), km(ASHA, 5.0, KESHAV, 5.0), KESHAV);

        assertThat(shares.get(ASHA)).isEqualByComparingTo("200");
        assertThat(shares.get(KESHAV)).isEqualByComparingTo("199");
    }

    @Test
    void paiseInTheFareStayWithTheBooker() {
        Map<UUID, BigDecimal> shares = FareSplit.split(rupees("612.40"), km(ASHA, 10.0, KESHAV, 10.0), KESHAV);

        assertThat(shares.get(ASHA)).isEqualByComparingTo("306");      // 306.20 → 306
        assertThat(shares.get(KESHAV)).isEqualByComparingTo("306.40");
    }

    @Test
    void theBookerNeverOwesANegativeAmount() {
        // Booker goes 1 km, three others 40 km each: rounding the others UP would leave the booker at −₹1
        UUID ravi = UUID.randomUUID();
        Map<UUID, BigDecimal> shares = FareSplit.split(rupees("50"), km(ASHA, 40.0, KESHAV, 40.0, NEHA, 40.0, ravi, 1.0), ravi);

        assertThat(shares.get(ravi).signum()).isGreaterThanOrEqualTo(0);
        assertThat(total(shares)).isEqualByComparingTo("50");
    }

    private static BigDecimal rupees(String amount) {
        return new BigDecimal(amount);
    }

    private static BigDecimal total(Map<UUID, BigDecimal> shares) {
        return shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // km(ASHA, 14.0, KESHAV, 9.7) → an ordered map of member → solo km
    private static Map<UUID, Double> km(Object... pairs) {
        Map<UUID, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((UUID) pairs[i], (Double) pairs[i + 1]);
        }
        return map;
    }
}
