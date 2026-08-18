package com.medpay.ledger.service;

import com.medpay.ledger.model.Claim;
import com.medpay.ledger.model.LedgerAccountType;
import com.medpay.ledger.model.LedgerDirection;
import com.medpay.ledger.model.LedgerJournal;
import com.medpay.ledger.model.ProviderAccount;
import com.medpay.ledger.exception.UnknownProviderException;
import com.medpay.ledger.repository.LedgerJournalRepository;
import com.medpay.ledger.repository.ProviderAccountRepository;
import com.medpay.ledger.util.MoneyMath;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class LedgerPostingService {

    private final LedgerJournalRepository journalRepository;
    private final ProviderAccountRepository providerAccountRepository;

    public LedgerPostingService(LedgerJournalRepository journalRepository,
                                ProviderAccountRepository providerAccountRepository) {
        this.journalRepository = journalRepository;
        this.providerAccountRepository = providerAccountRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID postAdjudication(Claim claim) {
        ProviderAccount account = managedProviderFor(claim);

        BigDecimal amount = assertPostableAllowance(claim);
        UUID groupId = UUID.randomUUID();

        LedgerJournal debit = LedgerJournal.of(
                groupId, claim, LedgerAccountType.PAYER_CLAIMS_EXPENSE,
                LedgerDirection.DEBIT, amount, null,
                "Claim adjudication expense " + claim.getClaimUuid());

        LedgerJournal credit = LedgerJournal.of(
                groupId, claim, LedgerAccountType.PROVIDER_PAYABLE,
                LedgerDirection.CREDIT, amount, account,
                "Provider payable " + claim.getClaimUuid());

        journalRepository.saveAll(List.of(debit, credit));
        account.accrue(amount);

        assertBalanced(List.of(debit, credit));
        return groupId;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID postReversal(Claim claim, UUID originalGroupId) {
        ProviderAccount account = managedProviderFor(claim);

        BigDecimal amount = assertPostableAllowance(claim);
        UUID groupId = UUID.randomUUID();

        LedgerJournal credit = LedgerJournal.reversing(
                groupId, originalGroupId, claim, LedgerAccountType.PAYER_CLAIMS_EXPENSE,
                LedgerDirection.CREDIT, amount, null,
                "Reversal of claim adjudication expense " + claim.getClaimUuid());

        LedgerJournal debit = LedgerJournal.reversing(
                groupId, originalGroupId, claim, LedgerAccountType.PROVIDER_PAYABLE,
                LedgerDirection.DEBIT, amount, account,
                "Recoupment of provider payable " + claim.getClaimUuid());

        journalRepository.saveAll(List.of(credit, debit));
        account.recoup(amount);

        assertBalanced(List.of(credit, debit));
        return groupId;
    }

    public UUID originalJournalGroupOf(Claim claim) {
        return journalRepository.findByClaimIdOrderByPostedAtAsc(claim.getId()).stream()
                .filter(journal -> journal.getReversesJournalGroupId() == null)
                .map(LedgerJournal::getJournalGroupId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Claim " + claim.getClaimUuid() + " has no original journal group to reverse"));
    }

    private ProviderAccount managedProviderFor(Claim claim) {
        String npi = claim.getProvider().getProviderNpi();
        return providerAccountRepository.findByProviderNpi(npi)
                .orElseThrow(() -> new UnknownProviderException(npi));
    }

    private static BigDecimal assertPostableAllowance(Claim claim) {
        BigDecimal allowed = claim.getAllowedAmount();
        if (allowed == null || allowed.signum() <= 0) {
            throw new IllegalStateException(
                    "Claim " + claim.getClaimUuid() + " has no positive allowed amount to post");
        }
        BigDecimal responsibility = claim.getPatientResponsibility();
        if (responsibility == null
                || !MoneyMath.equalToTheCent(allowed.add(responsibility), claim.getBilledAmount())) {
            throw new IllegalStateException(
                    "Claim " + claim.getClaimUuid()
                            + " violates allowed + patientResponsibility = billed");
        }
        return allowed;
    }

    private static void assertBalanced(List<LedgerJournal> group) {
        BigDecimal signedSum = group.stream()
                .map(LedgerJournal::signedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (signedSum.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Unbalanced journal group; signed sum=" + signedSum);
        }
    }
}
