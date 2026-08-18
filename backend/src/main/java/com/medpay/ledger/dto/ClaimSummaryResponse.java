package com.medpay.ledger.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.medpay.ledger.model.ClaimStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClaimSummaryResponse(
        UUID claimUuid,
        String providerNpi,
        LocalDate serviceDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal billedAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal allowedAmount,
        ClaimStatus status,
        Instant submittedAt,
        int lineCount) {
}
