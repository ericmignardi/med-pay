package com.medpay.ledger.exception;

import com.medpay.ledger.util.MoneyMath;

import java.math.BigDecimal;

public class LineItemSumMismatchException extends RuntimeException {

    private final BigDecimal headerAmount;
    private final BigDecimal lineSum;

    public LineItemSumMismatchException(BigDecimal headerAmount, BigDecimal lineSum) {
        super("Sum of claim line billed amounts does not equal the claim billed amount");
        this.headerAmount = MoneyMath.normalize(headerAmount);
        this.lineSum = MoneyMath.normalize(lineSum);
    }

    public BigDecimal getHeaderAmount() {
        return headerAmount;
    }

    public BigDecimal getLineSum() {
        return lineSum;
    }

    public BigDecimal difference() {
        return headerAmount.subtract(lineSum).abs();
    }
}
