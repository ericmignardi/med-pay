package com.medpay.ledger.service;

import com.medpay.ledger.model.Claim;
import com.medpay.ledger.model.ClaimStatus;
import com.medpay.ledger.model.ProviderAccount;
import com.medpay.ledger.repository.LedgerJournalRepository;
import com.medpay.ledger.repository.ProviderAccountRepository;
import com.medpay.ledger.testsupport.AbstractIntegrationTest;
import com.medpay.ledger.testsupport.ClaimFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerBalanceReconciliationTest extends AbstractIntegrationTest {

    @Autowired
    private LedgerPostingService ledgerPostingService;

    @Autowired
    private LedgerJournalRepository journalRepository;

    @Autowired
    private ProviderAccountRepository providerAccountRepository;

    @Autowired
    private ClaimFixtures fixtures;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("payable_balance equals the signed journal sum for that provider")
    void balanceReconcilesWithTheJournal() {
        ProviderAccount provider = fixtures.provider("1000000002");
        BigDecimal opening = provider.getPayableBalance();

        for (String allowed : new String[]{"400.00", "1250.50", "99.99"}) {
            Claim claim = fixtures.persistedClaim(
                    fixtures.processor(), provider, "5000.00", allowed, ClaimStatus.ADJUDICATED);
            transactionTemplate.execute(status -> ledgerPostingService.postAdjudication(claim));
        }

        ProviderAccount reloaded = providerAccountRepository.findById(provider.getId()).orElseThrow();

        assertThat(reloaded.getPayableBalance())
                .isEqualByComparingTo(opening.add(new BigDecimal("1750.49")));

        assertThat(journalRepository.sumProviderPayableBalance(provider.getId()))
                .isEqualByComparingTo(reloaded.getPayableBalance());
    }

    @Test
    @DisplayName("a reversal returns the balance to its pre-adjudication value")
    void reversalRestoresTheOpeningBalance() {
        ProviderAccount provider = fixtures.provider("1000000003");
        BigDecimal opening = provider.getPayableBalance();

        Claim claim = fixtures.persistedClaim(
                fixtures.processor(), provider, "9000.00", "7500.00", ClaimStatus.PAID);

        UUID originalGroup = transactionTemplate.execute(status ->
                ledgerPostingService.postAdjudication(claim));
        transactionTemplate.execute(status ->
                ledgerPostingService.postReversal(claim, originalGroup));

        ProviderAccount reloaded = providerAccountRepository.findById(provider.getId()).orElseThrow();

        assertThat(reloaded.getPayableBalance()).isEqualByComparingTo(opening);
        assertThat(journalRepository.sumProviderPayableBalance(provider.getId()))
                .isEqualByComparingTo(opening);
    }

    @Test
    @DisplayName("recoup refuses to overdraw the payable balance")
    void recoupGuardsAgainstOverdraw() {
        ProviderAccount provider = new ProviderAccount();
        provider.setPayableBalance(new BigDecimal("100.0000"));

        provider.recoup(new BigDecimal("100.00"));
        assertThat(provider.getPayableBalance()).isEqualByComparingTo("0.00");

        assertThatThrownBy(() -> provider.recoup(new BigDecimal("0.01")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("overdraw");
    }

    @Test
    @DisplayName("accrue and recoup reject non-positive movements")
    void balanceMovementsMustBePositive() {
        ProviderAccount provider = new ProviderAccount();

        assertThatThrownBy(() -> provider.accrue(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.accrue(new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.recoup(new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
