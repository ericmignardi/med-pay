package com.medpay.ledger.dto;

import com.medpay.ledger.model.ReversalReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReversalRequest(
        @NotNull ReversalReason reason,
        @NotBlank @Size(max = 1000) String note) {
}
