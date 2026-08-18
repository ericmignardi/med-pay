package com.medpay.ledger.service;

import com.medpay.ledger.dto.ClaimResponse;
import com.medpay.ledger.dto.ClaimSummaryResponse;
import com.medpay.ledger.dto.PageResponse;
import com.medpay.ledger.exception.ClaimNotFoundException;
import com.medpay.ledger.model.Claim;
import com.medpay.ledger.model.ClaimStatus;
import com.medpay.ledger.repository.ClaimRepository;
import com.medpay.ledger.repository.LedgerJournalRepository;
import com.medpay.ledger.security.AuthenticatedUser;
import com.medpay.ledger.util.PageRequestFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ClaimQueryService {

    private final ClaimRepository claimRepository;
    private final LedgerJournalRepository journalRepository;
    private final ClaimMapper claimMapper;

    public ClaimQueryService(ClaimRepository claimRepository,
                             LedgerJournalRepository journalRepository,
                             ClaimMapper claimMapper) {
        this.claimRepository = claimRepository;
        this.journalRepository = journalRepository;
        this.claimMapper = claimMapper;
    }

    public PageResponse<ClaimSummaryResponse> listOwn(AuthenticatedUser principal,
                                                      ClaimStatus status,
                                                      Integer page, Integer size) {
        Pageable pageable = PageRequestFactory.of(page, size);

        Page<Claim> claims = status == null
                ? claimRepository.findBySubmittedByIdOrderBySubmittedAtDesc(
                        principal.getUserId(), pageable)
                : claimRepository.findBySubmittedByIdAndStatusOrderBySubmittedAtDesc(
                        principal.getUserId(), status, pageable);

        return PageResponse.from(claims, claimMapper::toSummary);
    }

    public ClaimResponse findOwn(AuthenticatedUser principal, UUID claimUuid) {
        Claim claim = claimRepository
                .findWithLinesByClaimUuidAndSubmittedById(claimUuid, principal.getUserId())
                .orElseThrow(() -> new ClaimNotFoundException(claimUuid));

        return claimMapper.toResponse(claim,
                journalRepository.findByClaimIdOrderByPostedAtAsc(claim.getId()));
    }
}
