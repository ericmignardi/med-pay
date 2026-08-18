package com.medpay.ledger.service;

import com.medpay.ledger.dto.ClaimAuditResponse;
import com.medpay.ledger.dto.ClaimEventResponse;
import com.medpay.ledger.dto.JournalLineResponse;
import com.medpay.ledger.dto.PageResponse;
import com.medpay.ledger.exception.ClaimNotFoundException;
import com.medpay.ledger.model.Claim;
import com.medpay.ledger.model.LedgerJournal;
import com.medpay.ledger.model.OutboxEvent;
import com.medpay.ledger.repository.ClaimRepository;
import com.medpay.ledger.repository.JournalSpecifications;
import com.medpay.ledger.repository.LedgerJournalRepository;
import com.medpay.ledger.repository.OutboxEventRepository;
import com.medpay.ledger.util.PageRequestFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-only cross-tenant access for the AUDITOR role (FR-021, FR-022). Ownership is
 * deliberately ignored here — it is the only cross-tenant read in the system.
 */
@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Direction.DESC, "postedAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final LedgerJournalRepository journalRepository;
    private final ClaimRepository claimRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ClaimMapper claimMapper;

    public AuditQueryService(LedgerJournalRepository journalRepository,
                             ClaimRepository claimRepository,
                             OutboxEventRepository outboxEventRepository,
                             ClaimMapper claimMapper) {
        this.journalRepository = journalRepository;
        this.claimRepository = claimRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.claimMapper = claimMapper;
    }

    public PageResponse<JournalLineResponse> journals(String providerNpi, UUID claimUuid,
                                                      UUID journalGroupId,
                                                      Instant postedFrom, Instant postedTo,
                                                      Integer page, Integer size) {

        Specification<LedgerJournal> spec = JournalSpecifications.compose(
                providerNpi, claimUuid, journalGroupId, postedFrom, postedTo);

        Pageable pageable = PageRequestFactory.of(page, size, NEWEST_FIRST);
        Page<LedgerJournal> journals = journalRepository.findAll(spec, pageable);

        return PageResponse.from(journals, claimMapper::toJournalLine);
    }

    public ClaimAuditResponse claimHistory(UUID claimUuid) {
        Claim claim = claimRepository.findWithLinesByClaimUuid(claimUuid)
                .orElseThrow(() -> new ClaimNotFoundException(claimUuid));

        List<LedgerJournal> journals =
                journalRepository.findByClaimClaimUuidOrderByPostedAtAscIdAsc(claimUuid);

        List<ClaimEventResponse> events =
                outboxEventRepository.findByClaimClaimUuidOrderByCreatedAtAscIdAsc(claimUuid)
                        .stream()
                        .map(AuditQueryService::toEventResponse)
                        .toList();

        return new ClaimAuditResponse(
                claimMapper.toResponse(claim, journals),
                claimMapper.toJournalGroups(journals),
                events);
    }

    private static ClaimEventResponse toEventResponse(OutboxEvent event) {
        return new ClaimEventResponse(
                event.getEventUuid(),
                event.getEventType().name(),
                event.getCreatedAt(),
                event.getPublishedAt());
    }
}
