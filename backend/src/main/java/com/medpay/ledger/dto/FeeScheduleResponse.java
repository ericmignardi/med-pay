package com.medpay.ledger.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FeeScheduleResponse(
        String serviceCode,
        String description,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal contractedRate,
        LocalDate effectiveFrom,
        LocalDate effectiveTo) {
}
