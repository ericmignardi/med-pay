package com.medpay.ledger.dto;

import com.medpay.ledger.model.DenialReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewDenialRequest(
        @NotNull DenialReason reason,
        @NotBlank @Size(max = 1000) String note) {
}
