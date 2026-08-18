package com.medpay.ledger.service;

import com.medpay.ledger.model.Claim;
import com.medpay.ledger.model.ClaimEvent;
import com.medpay.ledger.model.ClaimStatus;
import com.medpay.ledger.model.OutboxEventType;
import com.medpay.ledger.util.AdjudicationPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

@Service
public class AdjudicationService {

    private final LedgerPostingService ledgerPostingService;
    private final OutboxService outboxService;

    public AdjudicationService(LedgerPostingService ledgerPostingService,
                               OutboxService outboxService) {
        this.ledgerPostingService = ledgerPostingService;
        this.outboxService = outboxService;
    }

    /**
     * MANDATORY by design: adjudication must never open its own transaction, or a ledger post
     * could commit independently of the claim it belongs to. The optimistic-lock retry lives at
     * the transaction boundary in {@link ClaimIntake} rather than here — retrying inside a
     * transaction that has already failed its flush cannot succeed.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ClaimStatus adjudicate(Claim claim) {
        BigDecimal billed = Objects.requireNonNull(claim.getBilledAmount(), "billedAmount");
        if (billed.signum() <= 0) {
            throw new IllegalStateException(
                    "Adjudication reached with a non-positive billed amount " + billed
                            + "; the controller boundary was bypassed");
        }

        if (AdjudicationPolicy.isBelowThreshold(billed)) {
            claim.apply(ClaimEvent.ADJUDICATE_BELOW_THRESHOLD);
            claim.setAdjudicatedAt(java.time.Instant.now());

            var groupId = ledgerPostingService.postAdjudication(claim);
            claim.apply(ClaimEvent.POST_LEDGER);

            outboxService.record(claim, OutboxEventType.CLAIM_PAID,
                    Map.of("journalGroupId", groupId));
            return claim.getStatus();
        }

        claim.apply(ClaimEvent.ADJUDICATE_AT_OR_ABOVE_THRESHOLD);
        outboxService.record(claim, OutboxEventType.CLAIM_FLAGGED,
                Map.of("reviewThreshold", AdjudicationPolicy.REVIEW_THRESHOLD.toPlainString()));
        return claim.getStatus();
    }
}
