package com.medpay.ledger.exception;

import java.util.UUID;

public class ClaimNotFoundException extends RuntimeException {

    private final UUID claimUuid;

    public ClaimNotFoundException(UUID claimUuid) {
        super("No claim with that identifier is visible to you");
        this.claimUuid = claimUuid;
    }

    public UUID getClaimUuid() {
        return claimUuid;
    }
}
