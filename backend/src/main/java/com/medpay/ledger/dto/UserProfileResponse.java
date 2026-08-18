package com.medpay.ledger.dto;

import java.util.List;
import java.util.UUID;

/**
 * Principal introspection for {@code GET /api/v1/auth/me} (FR-004). Built from
 * the security context alone — no database read.
 */
public record UserProfileResponse(
        UUID userUuid,
        String email,
        String fullName,
        List<String> roles) {
}
