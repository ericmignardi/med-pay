package com.medpay.ledger.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyMathTest {

    @Test
    @DisplayName("scale differences are equal to the cent, which BigDecimal.equals would deny")
    void scaleDoesNotAffectEquality() {
        BigDecimal loose = new BigDecimal("25000.0");
        BigDecimal exact = new BigDecimal("25000.00");

        assertThat(loose.equals(exact)).isFalse();
        assertThat(MoneyMath.equalToTheCent(loose, exact)).isTrue();
    }

    @ParameterizedTest(name = "billed={0} rate={1} -> allowed={2}")
    @CsvSource({
            "100.00, 125.00, 100.00",
            "200.00, 125.00, 125.00",
            "125.00, 125.00, 125.00",
            "0.01,   125.00, 0.01"
    })
    @DisplayName("FR-010: the allowance is the lesser of billed and the contracted rate")
    void lesserOfClamp(String billed, String rate, String expected) {
        assertThat(MoneyMath.allowedFor(new BigDecimal(billed), new BigDecimal(rate)))
                .isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("normalize rounds HALF_UP to two places")
    void normalizeRoundsHalfUp() {
        assertThat(MoneyMath.normalize(new BigDecimal("1.005"))).isEqualByComparingTo("1.01");
        assertThat(MoneyMath.normalize(new BigDecimal("1.004"))).isEqualByComparingTo("1.00");
        assertThat(MoneyMath.normalize(new BigDecimal("1.015"))).isEqualByComparingTo("1.02");
    }

    @Test
    @DisplayName("summation is exact across many small values where a double would drift")
    void summationIsExact() {
        List<BigDecimal> tenCents = java.util.Collections.nCopies(10, new BigDecimal("0.10"));

        assertThat(MoneyMath.sum(tenCents)).isEqualByComparingTo("1.00");

        double drifted = 0.0;
        for (int i = 0; i < 10; i++) {
            drifted += 0.1;
        }
        assertThat(drifted).isNotEqualTo(1.0);
    }

    @Test
    @DisplayName("an empty sum is zero, not null")
    void emptySumIsZero() {
        assertThat(MoneyMath.sum(List.of())).isEqualByComparingTo("0.00");
    }
}
