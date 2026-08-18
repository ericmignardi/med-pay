package com.medpay.ledger.util;

import java.math.BigDecimal;

public final class AdjudicationPolicy {

    public static final BigDecimal REVIEW_THRESHOLD = new BigDecimal("25000.00");

    private AdjudicationPolicy() {
    }

    public static boolean isBelowThreshold(BigDecimal billedAmount) {
        return billedAmount.compareTo(REVIEW_THRESHOLD) < 0;
    }
}
