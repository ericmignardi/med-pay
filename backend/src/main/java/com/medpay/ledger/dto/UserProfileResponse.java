package com.medpay.ledger.dto;

import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
        UUID userUuid,
        String email,
        String fullName,
        List<String> roles) {
}
