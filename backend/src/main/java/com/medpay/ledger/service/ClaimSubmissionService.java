package com.medpay.ledger.service;

import com.medpay.ledger.dto.ClaimResponse;
import com.medpay.ledger.dto.ClaimSubmissionRequest;
import com.medpay.ledger.exception.DuplicateClaimException;
import com.medpay.ledger.exception.UnknownProviderException;
import com.medpay.ledger.model.Claim;
import com.medpay.ledger.model.ClaimEvent;
import com.medpay.ledger.model.ClaimLine;
import com.medpay.ledger.model.ClaimStatus;
import com.medpay.ledger.model.OutboxEventType;
import com.medpay.ledger.model.ProviderAccount;
import com.medpay.ledger.model.User;
import com.medpay.ledger.repository.ClaimRepository;
import com.medpay.ledger.repository.LedgerJournalRepository;
import com.medpay.ledger.repository.ProviderAccountRepository;
import com.medpay.ledger.repository.UserRepository;
import com.medpay.ledger.security.AuthenticatedUser;
import com.medpay.ledger.util.ClaimFingerprintCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClaimSubmissionService {

    private final ClaimRepository claimRepository;
    private final UserRepository userRepository;
    private final ProviderAccountRepository providerAccountRepository;
    private final LedgerJournalRepository journalRepository;
    private final ClaimValidator claimValidator;
    private final ClaimPricingService pricingService;
    private final ClaimFingerprintCalculator fingerprintCalculator;
    private final AdjudicationService adjudicationService;
    private final OutboxService outboxService;
    private final ClaimMapper claimMapper;

    public ClaimSubmissionService(ClaimRepository claimRepository,
                                  UserRepository userRepository,
                                  ProviderAccountRepository providerAccountRepository,
                                  LedgerJournalRepository journalRepository,
                                  ClaimValidator claimValidator,
                                  ClaimPricingService pricingService,
                                  ClaimFingerprintCalculator fingerprintCalculator,
                                  AdjudicationService adjudicationService,
                                  OutboxService outboxService,
                                  ClaimMapper claimMapper) {
        this.claimRepository = claimRepository;
        this.userRepository = userRepository;
        this.providerAccountRepository = providerAccountRepository;
        this.journalRepository = journalRepository;
        this.claimValidator = claimValidator;
        this.pricingService = pricingService;
        this.fingerprintCalculator = fingerprintCalculator;
        this.adjudicationService = adjudicationService;
        this.outboxService = outboxService;
        this.claimMapper = claimMapper;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SubmissionOutcome submit(ClaimSubmissionRequest request,
                                    UUID idempotencyKey,
                                    AuthenticatedUser principal) {

        Optional<Claim> replayed = claimRepository
                .findBySubmittedByIdAndIdempotencyKey(principal.getUserId(), idempotencyKey);
        if (replayed.isPresent()) {
            return outcomeFor(replayed.get(), true);
        }

        claimValidator.assertLineSumMatchesHeader(request);

        ProviderAccount provider = providerAccountRepository
                .findByProviderNpi(request.providerNpi())
                .orElseThrow(() -> new UnknownProviderException(request.providerNpi()));

        ClaimPricing pricing = pricingService.price(request);

        String fingerprint = fingerprintCalculator.fingerprint(request);
        claimRepository.findActiveByFingerprint(fingerprint).ifPresent(existing -> {
            throw new DuplicateClaimException(existing.getClaimUuid(), fingerprint);
        });

        User submitter = userRepository.findById(principal.getUserId()).orElseThrow();

        Claim claim = new Claim();
        claim.setClaimUuid(UUID.randomUUID());
        claim.setSubmittedBy(submitter);
        claim.setProvider(provider);
        claim.setMemberReference(request.memberReference().trim());
        claim.setServiceDate(request.serviceDate());
        claim.setBilledAmount(request.billedAmount());
        claim.setClaimFingerprint(fingerprint);
        claim.setIdempotencyKey(idempotencyKey);

        for (ClaimPricing.PricedLine priced : pricing.lines()) {
            ClaimLine line = new ClaimLine();
            line.setLineNumber(priced.lineNumber());
            line.setServiceCode(priced.serviceCode());
            line.setDiagnosisCode(priced.diagnosisCode());
            line.setBilledAmount(priced.billedAmount());
            line.setAllowedAmount(priced.allowedAmount());
            line.setPatientResponsibility(priced.patientResponsibility());
            claim.addLine(line);
        }

        claim.apply(ClaimEvent.VALIDATE_OK);
        claim.setAllowedAmount(pricing.allowedAmount());
        claim.setPatientResponsibility(pricing.patientResponsibility());

        // A unique-constraint collision here means a concurrent submission won the race.
        // It is deliberately not caught: a failed flush leaves the persistence context
        // unusable, so recovery has to happen in a new transaction. ClaimIntake retries,
        // and the retry resolves it through the lookups above (FR-023).
        claimRepository.saveAndFlush(claim);

        outboxService.record(claim, OutboxEventType.CLAIM_SUBMITTED);
        adjudicationService.adjudicate(claim);
        claimRepository.saveAndFlush(claim);

        return outcomeFor(claim, false);
    }

    private SubmissionOutcome outcomeFor(Claim claim, boolean replay) {
        List<com.medpay.ledger.model.LedgerJournal> journals =
                journalRepository.findByClaimIdOrderByPostedAtAsc(claim.getId());
        boolean flagged = claim.getStatus() == ClaimStatus.FLAGGED_REVIEW;
        return new SubmissionOutcome(claimMapper.toResponse(claim, journals), flagged, replay);
    }

    public record SubmissionOutcome(ClaimResponse claim, boolean flagged, boolean replayed) {
    }
}
