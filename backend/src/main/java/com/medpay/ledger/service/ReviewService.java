package com.medpay.ledger.service;

import com.medpay.ledger.dto.ClaimResponse;
import com.medpay.ledger.dto.ClaimSummaryResponse;
import com.medpay.ledger.dto.PageResponse;
import com.medpay.ledger.dto.ReviewDecisionRequest;
import com.medpay.ledger.dto.ReviewDenialRequest;
import com.medpay.ledger.exception.ClaimNotFoundException;
import com.medpay.ledger.exception.SelfApprovalException;
import com.medpay.ledger.model.Claim;
import com.medpay.ledger.model.ClaimEvent;
import com.medpay.ledger.model.ClaimStatus;
import com.medpay.ledger.model.OutboxEventType;
import com.medpay.ledger.repository.ClaimRepository;
import com.medpay.ledger.repository.LedgerJournalRepository;
import com.medpay.ledger.repository.UserRepository;
import com.medpay.ledger.security.AuthenticatedUser;
import com.medpay.ledger.util.PageRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class ReviewService {

    private final ClaimRepository claimRepository;
    private final UserRepository userRepository;
    private final LedgerJournalRepository journalRepository;
    private final LedgerPostingService ledgerPostingService;
    private final OutboxService outboxService;
    private final ClaimMapper claimMapper;

    public ReviewService(ClaimRepository claimRepository,
                         UserRepository userRepository,
                         LedgerJournalRepository journalRepository,
                         LedgerPostingService ledgerPostingService,
                         OutboxService outboxService,
                         ClaimMapper claimMapper) {
        this.claimRepository = claimRepository;
        this.userRepository = userRepository;
        this.journalRepository = journalRepository;
        this.ledgerPostingService = ledgerPostingService;
        this.outboxService = outboxService;
        this.claimMapper = claimMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClaimSummaryResponse> queue(Integer page, Integer size) {
        return PageResponse.from(
                claimRepository.findByStatusOrderBySubmittedAtAsc(
                        ClaimStatus.FLAGGED_REVIEW, PageRequestFactory.of(page, size)),
                claimMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public ClaimResponse findFlagged(UUID claimUuid) {
        Claim claim = claimRepository
                .findWithLinesByClaimUuidAndStatus(claimUuid, ClaimStatus.FLAGGED_REVIEW)
                .orElseThrow(() -> new ClaimNotFoundException(claimUuid));

        return claimMapper.toResponse(claim,
                journalRepository.findByClaimIdOrderByPostedAtAsc(claim.getId()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClaimResponse approve(UUID claimUuid, ReviewDecisionRequest request,
                                 AuthenticatedUser reviewer) {
        Claim claim = requireClaim(claimUuid);
        assertNotSelfReview(claim, reviewer);

        claim.apply(ClaimEvent.REVIEWER_APPROVE);
        claim.setAdjudicatedAt(Instant.now());

        UUID groupId = ledgerPostingService.postAdjudication(claim);
        claim.apply(ClaimEvent.POST_LEDGER);

        stampReview(claim, reviewer, request.note());
        claimRepository.saveAndFlush(claim);

        outboxService.record(claim, OutboxEventType.CLAIM_PAID,
                Map.of("journalGroupId", groupId, "reviewedBy", reviewer.getUserUuid()));

        return claimMapper.toResponse(claim,
                journalRepository.findByClaimIdOrderByPostedAtAsc(claim.getId()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClaimResponse deny(UUID claimUuid, ReviewDenialRequest request,
                              AuthenticatedUser reviewer) {
        Claim claim = requireClaim(claimUuid);
        assertNotSelfReview(claim, reviewer);

        claim.apply(ClaimEvent.REVIEWER_DENY);
        claim.setDenialReason(request.reason());

        stampReview(claim, reviewer, request.note());
        claimRepository.saveAndFlush(claim);

        outboxService.record(claim, OutboxEventType.CLAIM_DENIED,
                Map.of("denialReason", request.reason(), "reviewedBy", reviewer.getUserUuid()));

        return claimMapper.toResponse(claim, java.util.List.of());
    }

    private Claim requireClaim(UUID claimUuid) {
        return claimRepository.findWithLinesByClaimUuid(claimUuid)
                .orElseThrow(() -> new ClaimNotFoundException(claimUuid));
    }

    private void assertNotSelfReview(Claim claim, AuthenticatedUser reviewer) {
        if (claim.getSubmittedBy().getUserUuid().equals(reviewer.getUserUuid())) {
            outboxService.recordIndependently(claim, OutboxEventType.SELF_APPROVAL_BLOCKED,
                    Map.of("attemptedBy", reviewer.getUserUuid()));
            throw new SelfApprovalException(claim.getClaimUuid(), reviewer.getUserUuid());
        }
    }

    private void stampReview(Claim claim, AuthenticatedUser reviewer, String note) {
        claim.setReviewedBy(userRepository.findByUserUuid(reviewer.getUserUuid()).orElseThrow());
        claim.setReviewedAt(Instant.now());
        claim.setReviewNote(note);
    }
}
