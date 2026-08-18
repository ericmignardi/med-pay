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
}
