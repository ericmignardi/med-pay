package com.medpay.ledger.service;

import com.medpay.ledger.model.Claim;
import com.medpay.ledger.model.ClaimStatus;
import com.medpay.ledger.model.LedgerAccountType;
import com.medpay.ledger.model.LedgerDirection;
import com.medpay.ledger.model.LedgerJournal;
import com.medpay.ledger.repository.LedgerJournalRepository;
import com.medpay.ledger.testsupport.AbstractIntegrationTest;
import com.medpay.ledger.testsupport.ClaimFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerInvariantTest extends AbstractIntegrationTest {

    @Autowired
    private LedgerPostingService ledgerPostingService;

    @Autowired
    private LedgerJournalRepository journalRepository;

    @Autowired
    private ClaimFixtures fixtures;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("FR-014: adjudication posts exactly one balanced debit/credit pair")
    void adjudicationPostsABalancedPair() {
        Claim claim = fixtures.persistedClaim("500.00", "400.00", ClaimStatus.ADJUDICATED);

        UUID groupId = transactionTemplate.execute(status ->
                ledgerPostingService.postAdjudication(claim));

        List<LedgerJournal> group = journalRepository.findByJournalGroupIdOrderByIdAsc(groupId);

        assertThat(group).hasSize(2);
        assertThat(journalRepository.sumSignedAmountByGroup(groupId))
                .isEqualByComparingTo(BigDecimal.ZERO);

        LedgerJournal debit = group.stream()
                .filter(j -> j.getDirection() == LedgerDirection.DEBIT).findFirst().orElseThrow();
        LedgerJournal credit = group.stream()
                .filter(j -> j.getDirection() == LedgerDirection.CREDIT).findFirst().orElseThrow();

        assertThat(debit.getAccountType()).isEqualTo(LedgerAccountType.PAYER_CLAIMS_EXPENSE);
        assertThat(debit.getProviderAccount()).isNull();
        assertThat(credit.getAccountType()).isEqualTo(LedgerAccountType.PROVIDER_PAYABLE);
        assertThat(credit.getProviderAccount()).isNotNull();
    }

    @Test
    @DisplayName("FR-014: the posted amount is the allowed amount, not the billed amount")
    void postedAmountIsTheAllowance() {
        Claim claim = fixtures.persistedClaim("500.00", "400.00", ClaimStatus.ADJUDICATED);

        UUID groupId = transactionTemplate.execute(status ->
                ledgerPostingService.postAdjudication(claim));

        assertThat(journalRepository.findByJournalGroupIdOrderByIdAsc(groupId))
                .allSatisfy(journal -> assertThat(journal.getAmount())
                        .isEqualByComparingTo("400.00"));
    }

    @Test
    @DisplayName("FR-014: postAdjudication outside a transaction throws rather than posting")
    void mandatoryPropagationRefusesToOpenItsOwnTransaction() {
        Claim claim = fixtures.persistedClaim("500.00", "400.00", ClaimStatus.ADJUDICATED);
        long before = journalRepository.count();

        assertThatThrownBy(() -> ledgerPostingService.postAdjudication(claim))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(journalRepository.count())
                .as("a non-atomic post must leave no rows behind")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("FR-015: reversal inverts direction under a new group linked to the original")
    void reversalInvertsDirectionsUnderANewGroup() {
        Claim claim = fixtures.persistedClaim("500.00", "400.00", ClaimStatus.PAID);

        UUID originalGroup = transactionTemplate.execute(status ->
                ledgerPostingService.postAdjudication(claim));
        UUID reversalGroup = transactionTemplate.execute(status ->
                ledgerPostingService.postReversal(claim, originalGroup));

        assertThat(reversalGroup).isNotEqualTo(originalGroup);

        List<LedgerJournal> original = journalRepository
                .findByJournalGroupIdOrderByIdAsc(originalGroup);
        List<LedgerJournal> reversal = journalRepository
                .findByJournalGroupIdOrderByIdAsc(reversalGroup);

        assertThat(original)
                .as("a reversal never touches the original rows")
                .allSatisfy(j -> assertThat(j.getReversesJournalGroupId()).isNull());

        assertThat(reversal).hasSize(2).allSatisfy(j ->
                assertThat(j.getReversesJournalGroupId()).isEqualTo(originalGroup));

        assertThat(reversal.stream()
                .filter(j -> j.getAccountType() == LedgerAccountType.PAYER_CLAIMS_EXPENSE)
                .findFirst().orElseThrow().getDirection())
                .isEqualTo(LedgerDirection.CREDIT);
        assertThat(reversal.stream()
                .filter(j -> j.getAccountType() == LedgerAccountType.PROVIDER_PAYABLE)
                .findFirst().orElseThrow().getDirection())
                .isEqualTo(LedgerDirection.DEBIT);

        assertThat(journalRepository.sumSignedAmountByGroup(reversalGroup))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("FR-015: ux_ledger_journals_reverses rejects a second reversal of one group")
    void aGroupCanBeReversedAtMostOnce() {
        Claim claim = fixtures.persistedClaim("500.00", "400.00", ClaimStatus.PAID);

        UUID originalGroup = transactionTemplate.execute(status ->
                ledgerPostingService.postAdjudication(claim));
        transactionTemplate.execute(status ->
                ledgerPostingService.postReversal(claim, originalGroup));

        assertThatThrownBy(() -> transactionTemplate.execute(status ->
                ledgerPostingService.postReversal(claim, originalGroup)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("FR-014: a claim with no allowance cannot be posted")
    void unpricedClaimCannotBePosted() {
        Claim claim = fixtures.persistedClaim("500.00", null, ClaimStatus.FLAGGED_REVIEW);

        assertThatThrownBy(() -> transactionTemplate.execute(status ->
                ledgerPostingService.postAdjudication(claim)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("FR-014: amounts are always positive; the sign lives in the direction alone")
    void negativeAmountsAreRejectedByTheFactory() {
        Claim claim = fixtures.persistedClaim("500.00", "400.00", ClaimStatus.ADJUDICATED);

        assertThatThrownBy(() -> LedgerJournal.of(UUID.randomUUID(), claim,
                LedgerAccountType.PAYER_CLAIMS_EXPENSE, LedgerDirection.DEBIT,
                new BigDecimal("-1.00"), null, "negative"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> LedgerJournal.of(UUID.randomUUID(), claim,
                LedgerAccountType.PAYER_CLAIMS_EXPENSE, LedgerDirection.DEBIT,
                BigDecimal.ZERO, null, "zero"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the provider linkage rule is enforced before the database sees the row")
    void providerLinkageIsEnforcedByTheFactory() {
        Claim claim = fixtures.persistedClaim("500.00", "400.00", ClaimStatus.ADJUDICATED);

        assertThatThrownBy(() -> LedgerJournal.of(UUID.randomUUID(), claim,
                LedgerAccountType.PROVIDER_PAYABLE, LedgerDirection.CREDIT,
                new BigDecimal("400.00"), null, "missing provider"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> LedgerJournal.of(UUID.randomUUID(), claim,
                LedgerAccountType.PAYER_CLAIMS_EXPENSE, LedgerDirection.DEBIT,
                new BigDecimal("400.00"), fixtures.provider(), "provider on expense"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("FR-014: every journal group in the database sums to zero")
    void everyJournalGroupBalances() {
        fixtures.persistedClaim("500.00", "400.00", ClaimStatus.ADJUDICATED);

        for (int i = 0; i < 3; i++) {
            Claim claim = fixtures.persistedClaim("900.00", "700.00", ClaimStatus.ADJUDICATED);
            transactionTemplate.execute(status -> ledgerPostingService.postAdjudication(claim));
        }

        assertThat(journalRepository.findUnbalancedJournalGroups()).isEmpty();
    }
}
