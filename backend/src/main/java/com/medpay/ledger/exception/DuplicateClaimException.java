package com.medpay.ledger.exception;

import java.util.UUID;

public class DuplicateClaimException extends RuntimeException {

    private final UUID existingClaimUuid;
    private final String fingerprint;

    public DuplicateClaimException(UUID existingClaimUuid, String fingerprint) {
        super("An active claim already exists for this service encounter");
        this.existingClaimUuid = existingClaimUuid;
        this.fingerprint = fingerprint;
    }

    public UUID getExistingClaimUuid() {
        return existingClaimUuid;
    }

    public String getFingerprint() {
        return fingerprint;
    }
}
