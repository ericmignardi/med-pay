package com.medpay.ledger.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ClaimLineRequest(

        @NotBlank
        @Pattern(regexp = "^[A-Z0-9]{5}$")
        String serviceCode,

        @NotBlank
        @Size(max = 8)
        String diagnosisCode,

        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 15, fraction = 2)
        BigDecimal billedAmount) {
}
