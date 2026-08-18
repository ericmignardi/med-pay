package com.medpay.ledger.service;

import com.medpay.ledger.dto.ClaimResponse;
import com.medpay.ledger.dto.ReversalRequest;
import com.medpay.ledger.exception.ClaimNotFoundException;
import com.medpay.ledger.model.Claim;
import com.medpay.ledger.model.ClaimEvent;
import com.medpay.ledger.model.OutboxEventType;
import com.medpay.ledger.repository.ClaimRepository;
import com.medpay.ledger.repository.LedgerJournalRepository;
import com.medpay.ledger.repository.UserRepository;
import com.medpay.ledger.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class ReversalService {

    private final ClaimRepository claimRepository;
    private final UserRepository userRepository;
    private final LedgerJournalRepository journalRepository;
    private final LedgerPostingService ledgerPostingService;
    private final OutboxService outboxService;
    private final ClaimMapper claimMapper;

    public ReversalService(ClaimRepository claimRepository,
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClaimResponse reverse(UUID claimUuid, ReversalRequest request,
                                 AuthenticatedUser reviewer) {

        Claim claim = claimRepository.findWithLinesByClaimUuid(claimUuid)
                .orElseThrow(() -> new ClaimNotFoundException(claimUuid));

        claim.apply(ClaimEvent.REVERSE);

        UUID originalGroup = ledgerPostingService.originalJournalGroupOf(claim);
        UUID reversalGroup = ledgerPostingService.postReversal(claim, originalGroup);

        claim.setReviewedBy(userRepository.findByUserUuid(reviewer.getUserUuid()).orElseThrow());
        claim.setReviewedAt(Instant.now());
        claim.setReviewNote(request.note());
        claimRepository.saveAndFlush(claim);

        outboxService.record(claim, OutboxEventType.CLAIM_REVERSED,
                Map.of("reversalReason", request.reason(),
                        "reversesJournalGroupId", originalGroup,
                        "journalGroupId", reversalGroup,
                        "reversedBy", reviewer.getUserUuid()));

        return claimMapper.toResponse(claim,
                journalRepository.findByClaimIdOrderByPostedAtAsc(claim.getId()));
    }
}
