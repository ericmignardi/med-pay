package com.medpay.ledger.model;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "claims")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_uuid", nullable = false, unique = true, updatable = false)
    private UUID claimUuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by_user_id", nullable = false, updatable = false)
    private User submittedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_npi", referencedColumnName = "provider_npi",
            nullable = false, updatable = false)
    private ProviderAccount provider;

    @Column(name = "member_reference", nullable = false, length = 64, updatable = false)
    private String memberReference;

    @Column(name = "service_date", nullable = false, updatable = false)
    private LocalDate serviceDate;

    @Column(name = "billed_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal billedAmount;

    @Column(name = "allowed_amount", precision = 19, scale = 4)
    private BigDecimal allowedAmount;

    @Column(name = "patient_responsibility", precision = 19, scale = 4)
    private BigDecimal patientResponsibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ClaimStatus status;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "claim_fingerprint", nullable = false, length = 64, updatable = false)
    private String claimFingerprint;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private UUID idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "denial_reason", length = 40)
    private DenialReason denialReason;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "adjudicated_at")
    private Instant adjudicatedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<ClaimLine> lines = new ArrayList<>();

    public void addLine(ClaimLine line) {
        lines.add(line);
        line.setClaim(this);
    }
}
