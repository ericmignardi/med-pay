package com.medpay.ledger.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ClaimSubmissionRequest(

        @NotBlank
        @Pattern(regexp = "^\\d{10}$")
        String providerNpi,

        @NotBlank
        @Size(max = 64)
        String memberReference,

        @NotNull
        @PastOrPresent
        LocalDate serviceDate,

        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 15, fraction = 2)
        BigDecimal billedAmount,

        @NotEmpty
        @Size(max = 20)
        @Valid
        List<ClaimLineRequest> lines) {
}
