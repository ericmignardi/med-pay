package com.medpay.ledger.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "claim_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClaimLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false, updatable = false)
    private Claim claim;

    @Column(name = "line_number", nullable = false)
    private Short lineNumber;

    @Column(name = "service_code", nullable = false, length = 5)
    private String serviceCode;

    @Column(name = "diagnosis_code", nullable = false, length = 8)
    private String diagnosisCode;

    @Column(name = "billed_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal billedAmount;

    @Column(name = "allowed_amount", precision = 19, scale = 4)
    private BigDecimal allowedAmount;

    @Column(name = "patient_responsibility", precision = 19, scale = 4)
    private BigDecimal patientResponsibility;
}
