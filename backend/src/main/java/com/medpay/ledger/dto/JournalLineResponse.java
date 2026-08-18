package com.medpay.ledger.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.medpay.ledger.model.LedgerAccountType;
import com.medpay.ledger.model.LedgerDirection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record JournalLineResponse(
        UUID journalGroupId,
        UUID claimUuid,
        String providerNpi,
        LedgerAccountType accountType,
        LedgerDirection direction,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String memo,
        UUID reversesJournalGroupId,
        Instant postedAt) {
}
