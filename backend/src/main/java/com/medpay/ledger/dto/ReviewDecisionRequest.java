package com.medpay.ledger.dto;

import jakarta.validation.constraints.Size;

public record ReviewDecisionRequest(@Size(max = 1000) String note) {
}
