package com.medpay.ledger.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JournalGroupResponse(
        UUID journalGroupId,
        UUID reversesJournalGroupId,
        Instant postedAt,
        List<JournalLineResponse> lines) {
}
