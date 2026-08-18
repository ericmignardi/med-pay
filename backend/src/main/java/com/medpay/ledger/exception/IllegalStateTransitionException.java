package com.medpay.ledger.exception;

import com.medpay.ledger.model.ClaimEvent;
import com.medpay.ledger.model.ClaimStatus;

import java.util.Set;

public class IllegalStateTransitionException extends RuntimeException {

    private final ClaimStatus currentStatus;
    private final ClaimEvent attemptedEvent;
    private final Set<ClaimEvent> allowedEvents;

    public IllegalStateTransitionException(ClaimStatus currentStatus, ClaimEvent attemptedEvent,
                                           Set<ClaimEvent> allowedEvents) {
        super("Event " + attemptedEvent + " is not legal from status " + currentStatus);
        this.currentStatus = currentStatus;
        this.attemptedEvent = attemptedEvent;
        this.allowedEvents = Set.copyOf(allowedEvents);
    }

    public ClaimStatus getCurrentStatus() {
        return currentStatus;
    }

    public ClaimEvent getAttemptedEvent() {
        return attemptedEvent;
    }

    public Set<ClaimEvent> getAllowedEvents() {
        return allowedEvents;
    }
}
