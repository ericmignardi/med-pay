package com.medpay.ledger.dto;

import java.time.Instant;
import java.util.UUID;

public record ClaimEventResponse(
        UUID eventUuid,
        String eventType,
        Instant createdAt,
        Instant publishedAt) {
}
