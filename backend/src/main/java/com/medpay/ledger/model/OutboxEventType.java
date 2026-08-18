package com.medpay.ledger.model;

public enum OutboxEventType {
    CLAIM_SUBMITTED,
    CLAIM_PAID,
    CLAIM_FLAGGED,
    CLAIM_DENIED,
    CLAIM_REVERSED,
    SELF_APPROVAL_BLOCKED
}
