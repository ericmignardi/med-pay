package com.medpay.ledger.service;

import java.math.BigDecimal;
import java.util.List;

public record ClaimPricing(
        BigDecimal allowedAmount,
        BigDecimal patientResponsibility,
        List<PricedLine> lines) {

    public record PricedLine(
            short lineNumber,
            String serviceCode,
            String diagnosisCode,
            BigDecimal billedAmount,
            BigDecimal allowedAmount,
            BigDecimal patientResponsibility) {
    }
}
