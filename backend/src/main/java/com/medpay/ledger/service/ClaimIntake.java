package com.medpay.ledger.service;

import com.medpay.ledger.dto.ClaimSubmissionRequest;
import com.medpay.ledger.security.AuthenticatedUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * FR-023. The retry boundary for claim intake.
 *
 * <p>This exists as a separate bean on purpose. A retry is only meaningful <em>outside</em> the
 * transaction: once a flush fails on a version mismatch or a unique constraint, the transaction
 * is doomed and its persistence context is unusable, so re-running anything inside it cannot
 * succeed. Delegating from a non-transactional bean means each attempt begins a fresh
 * transaction, which re-reads the idempotency key and the fingerprint before inserting.
 *
 * <p>That re-read is what makes the concurrent double-submit converge: the loser of the race
 * finds the winner's row on its second attempt and returns it as a replay instead of failing.
 */
@Service
public class ClaimIntake {

    private final ClaimSubmissionService submissionService;

    public ClaimIntake(ClaimSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @Retryable(
            includes = {OptimisticLockingFailureException.class,
                    DataIntegrityViolationException.class},
            maxRetries = 3, delay = 50, timeUnit = TimeUnit.MILLISECONDS)
    public ClaimSubmissionService.SubmissionOutcome submit(ClaimSubmissionRequest request,
                                                           UUID idempotencyKey,
                                                           AuthenticatedUser principal) {
        return submissionService.submit(request, idempotencyKey, principal);
    }
}
