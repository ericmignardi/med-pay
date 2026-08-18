package com.medpay.ledger.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.medpay.ledger.model.ClaimStatus;
import com.medpay.ledger.model.DenialReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ClaimResponse(
        UUID claimUuid,
        String providerNpi,
        String providerName,
        String memberReference,
        LocalDate serviceDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal billedAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal allowedAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal patientResponsibility,
        ClaimStatus status,
        Instant submittedAt,
        Instant adjudicatedAt,
        Instant reviewedAt,
        String reviewNote,
        DenialReason denialReason,
        List<ClaimLineResponse> lines,
        List<JournalGroupResponse> journalGroups) {
}
