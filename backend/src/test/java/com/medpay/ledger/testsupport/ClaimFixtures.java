package com.medpay.ledger.testsupport;

import com.medpay.ledger.model.Claim;
import com.medpay.ledger.model.ClaimEvent;
import com.medpay.ledger.model.ClaimLine;
import com.medpay.ledger.model.ClaimStatus;
import com.medpay.ledger.model.ProviderAccount;
import com.medpay.ledger.model.User;
import com.medpay.ledger.repository.ClaimRepository;
import com.medpay.ledger.repository.ProviderAccountRepository;
import com.medpay.ledger.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Component
public class ClaimFixtures {

    public static final String PROCESSOR_EMAIL = "processor@medpay.test";
    public static final String REVIEWER_EMAIL = "reviewer@medpay.test";
    public static final String DEFAULT_NPI = "1000000001";

    private final ClaimRepository claimRepository;
    private final UserRepository userRepository;
    private final ProviderAccountRepository providerAccountRepository;

    public ClaimFixtures(ClaimRepository claimRepository,
                         UserRepository userRepository,
                         ProviderAccountRepository providerAccountRepository) {
        this.claimRepository = claimRepository;
        this.userRepository = userRepository;
        this.providerAccountRepository = providerAccountRepository;
    }

    public User processor() {
        return userRepository.findByEmailIgnoreCase(PROCESSOR_EMAIL).orElseThrow();
    }

    public User reviewer() {
        return userRepository.findByEmailIgnoreCase(REVIEWER_EMAIL).orElseThrow();
    }

    public ProviderAccount provider() {
        return providerAccountRepository.findByProviderNpi(DEFAULT_NPI).orElseThrow();
    }

    public ProviderAccount provider(String npi) {
        return providerAccountRepository.findByProviderNpi(npi).orElseThrow();
    }

    public Claim persistedClaim(String billed, String allowed, ClaimStatus status) {
        return persistedClaim(processor(), provider(), billed, allowed, status);
    }

    public Claim persistedClaim(User submitter, ProviderAccount provider,
                                String billed, String allowed, ClaimStatus status) {

        BigDecimal billedAmount = new BigDecimal(billed);
        BigDecimal allowedAmount = allowed == null ? null : new BigDecimal(allowed);

        Claim claim = new Claim();
        claim.setClaimUuid(UUID.randomUUID());
        claim.setSubmittedBy(submitter);
        claim.setProvider(provider);
        claim.setMemberReference("MBR-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12).toUpperCase());
        claim.setServiceDate(LocalDate.now().minusDays(1));
        claim.setBilledAmount(billedAmount);
        claim.setAllowedAmount(allowedAmount);
        claim.setPatientResponsibility(
                allowedAmount == null ? null : billedAmount.subtract(allowedAmount));
        driveTo(claim, status);
        claim.setClaimFingerprint(randomFingerprint());
        claim.setIdempotencyKey(UUID.randomUUID());

        ClaimLine line = new ClaimLine();
        line.setLineNumber((short) 1);
        line.setServiceCode("MP101");
        line.setDiagnosisCode("E1165");
        line.setBilledAmount(billedAmount);
        line.setAllowedAmount(allowedAmount);
        line.setPatientResponsibility(
                allowedAmount == null ? null : billedAmount.subtract(allowedAmount));
        claim.addLine(line);

        return claimRepository.saveAndFlush(claim);
    }

    public static void driveTo(Claim claim, ClaimStatus target) {
        if (target == ClaimStatus.RECEIVED) {
            return;
        }
        claim.apply(ClaimEvent.VALIDATE_OK);
        if (target == ClaimStatus.VALIDATED) {
            return;
        }
        if (target == ClaimStatus.FLAGGED_REVIEW || target == ClaimStatus.DENIED) {
            claim.apply(ClaimEvent.ADJUDICATE_AT_OR_ABOVE_THRESHOLD);
            if (target == ClaimStatus.DENIED) {
                claim.apply(ClaimEvent.REVIEWER_DENY);
            }
            return;
        }
        claim.apply(ClaimEvent.ADJUDICATE_BELOW_THRESHOLD);
        if (target == ClaimStatus.ADJUDICATED) {
            return;
        }
        claim.apply(ClaimEvent.POST_LEDGER);
        if (target == ClaimStatus.PAID) {
            return;
        }
        claim.apply(ClaimEvent.REVERSE);
    }

    public static String randomFingerprint() {
        return (UUID.randomUUID().toString() + UUID.randomUUID())
                .replace("-", "").substring(0, 64);
    }
}
