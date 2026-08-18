package com.medpay.ledger.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyMath {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final int STORAGE_SCALE = 4;

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private MoneyMath() {
    }

    public static BigDecimal normalize(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal allowedFor(BigDecimal billed, BigDecimal contractedRate) {
        return normalize(billed.compareTo(contractedRate) <= 0 ? billed : contractedRate);
    }

    public static boolean equalToTheCent(BigDecimal left, BigDecimal right) {
        return normalize(left).compareTo(normalize(right)) == 0;
    }

    public static BigDecimal sum(Iterable<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(value);
        }
        return normalize(total);
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
