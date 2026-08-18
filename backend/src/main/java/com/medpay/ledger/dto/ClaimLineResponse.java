package com.medpay.ledger.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

public record ClaimLineResponse(
        short lineNumber,
        String serviceCode,
        String diagnosisCode,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal billedAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal allowedAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal patientResponsibility) {
}
