package com.medpay.ledger.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_journals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "journal_group_id", nullable = false, updatable = false)
    private UUID journalGroupId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false, updatable = false)
    private Claim claim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_account_id", updatable = false)
    private ProviderAccount providerAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 32, updatable = false)
    private LedgerAccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6, updatable = false)
    private LedgerDirection direction;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 255, updatable = false)
    private String memo;

    @Column(name = "reverses_journal_group_id", updatable = false)
    private UUID reversesJournalGroupId;

    @Column(name = "posted_at", nullable = false, updatable = false)
    private Instant postedAt = Instant.now();

    private LedgerJournal(UUID journalGroupId, Claim claim, LedgerAccountType accountType,
                          LedgerDirection direction, BigDecimal amount,
                          ProviderAccount providerAccount, String memo,
                          UUID reversesJournalGroupId) {

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Ledger amounts are always positive; sign lives in the direction. Got " + amount);
        }
        boolean providerLinked = providerAccount != null;
        if (accountType == LedgerAccountType.PROVIDER_PAYABLE && !providerLinked) {
            throw new IllegalArgumentException("PROVIDER_PAYABLE requires a provider account");
        }
        if (accountType == LedgerAccountType.PAYER_CLAIMS_EXPENSE && providerLinked) {
            throw new IllegalArgumentException("PAYER_CLAIMS_EXPENSE must not carry a provider account");
        }

        this.journalGroupId = journalGroupId;
        this.claim = claim;
        this.accountType = accountType;
        this.direction = direction;
        this.amount = amount;
        this.providerAccount = providerAccount;
        this.memo = memo;
        this.reversesJournalGroupId = reversesJournalGroupId;
        this.postedAt = Instant.now();
    }

    public static LedgerJournal of(UUID journalGroupId, Claim claim, LedgerAccountType accountType,
                                   LedgerDirection direction, BigDecimal amount,
                                   ProviderAccount providerAccount, String memo) {
        return new LedgerJournal(journalGroupId, claim, accountType, direction, amount,
                providerAccount, memo, null);
    }

    public static LedgerJournal reversing(UUID journalGroupId, UUID reversesJournalGroupId,
                                          Claim claim, LedgerAccountType accountType,
                                          LedgerDirection direction, BigDecimal amount,
                                          ProviderAccount providerAccount, String memo) {
        return new LedgerJournal(journalGroupId, claim, accountType, direction, amount,
                providerAccount, memo, reversesJournalGroupId);
    }

    public BigDecimal signedAmount() {
        return direction == LedgerDirection.DEBIT ? amount : amount.negate();
    }
}
