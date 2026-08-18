package com.medpay.ledger.util;

import com.medpay.ledger.exception.IllegalStateTransitionException;
import com.medpay.ledger.model.ClaimEvent;
import com.medpay.ledger.model.ClaimStatus;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class ClaimStateMachine {

    private static final Map<ClaimStatus, Map<ClaimEvent, ClaimStatus>> TRANSITIONS = Map.of(
            ClaimStatus.RECEIVED, Map.of(
                    ClaimEvent.VALIDATE_OK, ClaimStatus.VALIDATED),
            ClaimStatus.VALIDATED, Map.of(
                    ClaimEvent.ADJUDICATE_BELOW_THRESHOLD, ClaimStatus.ADJUDICATED,
                    ClaimEvent.ADJUDICATE_AT_OR_ABOVE_THRESHOLD, ClaimStatus.FLAGGED_REVIEW),
            ClaimStatus.ADJUDICATED, Map.of(
                    ClaimEvent.POST_LEDGER, ClaimStatus.PAID),
            ClaimStatus.FLAGGED_REVIEW, Map.of(
                    ClaimEvent.REVIEWER_APPROVE, ClaimStatus.ADJUDICATED,
                    ClaimEvent.REVIEWER_DENY, ClaimStatus.DENIED),
            ClaimStatus.PAID, Map.of(
                    ClaimEvent.REVERSE, ClaimStatus.REVERSED),
            ClaimStatus.DENIED, Map.of(),
            ClaimStatus.REVERSED, Map.of());

    private ClaimStateMachine() {
    }

    public static ClaimStatus transition(ClaimStatus from, ClaimEvent event) {
        ClaimStatus to = TRANSITIONS.getOrDefault(from, Map.of()).get(event);
        if (to == null) {
            throw new IllegalStateTransitionException(from, event, allowedEvents(from));
        }
        return to;
    }

    public static Set<ClaimEvent> allowedEvents(ClaimStatus from) {
        Map<ClaimEvent, ClaimStatus> allowed = TRANSITIONS.getOrDefault(from, Map.of());
        return allowed.isEmpty() ? EnumSet.noneOf(ClaimEvent.class) : EnumSet.copyOf(allowed.keySet());
    }

    public static boolean isTerminal(ClaimStatus status) {
        return allowedEvents(status).isEmpty();
    }
}
