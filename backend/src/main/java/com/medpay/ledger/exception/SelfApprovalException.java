package com.medpay.ledger.exception;

import java.util.UUID;

public class SelfApprovalException extends RuntimeException {

    private final UUID claimUuid;
    private final UUID userUuid;

    public SelfApprovalException(UUID claimUuid, UUID userUuid) {
        super("A claim may not be reviewed by the user who submitted it");
        this.claimUuid = claimUuid;
        this.userUuid = userUuid;
    }

    public UUID getClaimUuid() {
        return claimUuid;
    }

    public UUID getUserUuid() {
        return userUuid;
    }
}
