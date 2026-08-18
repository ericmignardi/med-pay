package com.medpay.ledger.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LoginResponse(
        String token,
        Instant expiresAt,
        UUID userUuid,
        String email,
        String fullName,
        List<String> roles) {
}
