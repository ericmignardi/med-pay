package com.medpay.ledger.service;

import com.medpay.ledger.dto.ClaimLineRequest;
import com.medpay.ledger.exception.UnknownServiceCodeException;
import com.medpay.ledger.testsupport.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimPricingServiceIT extends AbstractIntegrationTest {

    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 3, 14);

    @Autowired
    private ClaimPricingService pricingService;

    @Autowired
    private FeeScheduleService feeScheduleService;

    private ClaimLineRequest line(String code, String billed) {
        return new ClaimLineRequest(code, "E1165", new BigDecimal(billed));
    }

    @Test
    @DisplayName("TC-B-012: an unknown service code is rejected rather than priced at zero")
    void unknownServiceCodeIsRejected() {
        assertThatThrownBy(() ->
                pricingService.price(List.of(line("ZZZZZ", "100.00")), SERVICE_DATE))
                .isInstanceOf(UnknownServiceCodeException.class)
                .satisfies(thrown -> assertThat(
                        ((UnknownServiceCodeException) thrown).getServiceCode()).isEqualTo("ZZZZZ"));
    }

    @Test
    @DisplayName("FR-010: billed above the contracted rate clamps to the rate, residual is the member's")
    void billedAboveRateClampsToContractedRate() {
        ClaimPricing pricing = pricingService.price(List.of(line("MP101", "200.00")), SERVICE_DATE);

        assertThat(pricing.allowedAmount()).isEqualByComparingTo("125.00");
        assertThat(pricing.patientResponsibility()).isEqualByComparingTo("75.00");
        assertThat(pricing.lines()).singleElement().satisfies(priced -> {
            assertThat(priced.lineNumber()).isEqualTo((short) 1);
            assertThat(priced.allowedAmount()).isEqualByComparingTo("125.00");
            assertThat(priced.patientResponsibility()).isEqualByComparingTo("75.00");
        });
    }

    @Test
    @DisplayName("FR-010: billed below the contracted rate is allowed in full, nothing is the member's")
    void billedBelowRateIsAllowedInFull() {
        ClaimPricing pricing = pricingService.price(List.of(line("MP101", "80.00")), SERVICE_DATE);

        assertThat(pricing.allowedAmount()).isEqualByComparingTo("80.00");
        assertThat(pricing.patientResponsibility()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("FR-010: allowed + patientResponsibility = billed holds at the header across mixed lines")
    void headerInvariantHoldsByConstruction() {
        List<ClaimLineRequest> lines = List.of(
                line("MP101", "200.00"),
                line("DX202", "20.00"),
                line("SX301", "5000.00"));

        ClaimPricing pricing = pricingService.price(lines, SERVICE_DATE);
        BigDecimal billedTotal = new BigDecimal("5220.00");

        assertThat(pricing.allowedAmount().add(pricing.patientResponsibility()))
                .isEqualByComparingTo(billedTotal);

        assertThat(pricing.allowedAmount()).isEqualByComparingTo("4345.00");
        assertThat(pricing.patientResponsibility()).isEqualByComparingTo("875.00");
    }

    @Test
    @DisplayName("line numbers are assigned 1..n in submission order")
    void lineNumbersAreSequential() {
        ClaimPricing pricing = pricingService.price(
                List.of(line("MP101", "10.00"), line("MP102", "10.00"), line("MP103", "10.00")),
                SERVICE_DATE);

        assertThat(pricing.lines()).extracting(ClaimPricing.PricedLine::lineNumber)
                .containsExactly((short) 1, (short) 2, (short) 3);
    }

    @Test
    @DisplayName("FR-010: the rate in force on the date of service is used, not the latest rate")
    void rateLookupHonoursTheServiceDate() {
        assertThat(feeScheduleService.rateFor("RT501", LocalDate.of(2022, 6, 15)))
                .isEqualByComparingTo("780.0000");
        assertThat(feeScheduleService.rateFor("RT501", LocalDate.of(2023, 6, 15)))
                .isEqualByComparingTo("845.0000");

        assertThatThrownBy(() -> feeScheduleService.rateFor("RT501", LocalDate.of(2019, 1, 1)))
                .isInstanceOf(UnknownServiceCodeException.class);
    }

    @Test
    @DisplayName("a lowercase service code resolves to the same contracted rate")
    void serviceCodeLookupIsCaseInsensitive() {
        assertThat(feeScheduleService.rateFor("mp101", SERVICE_DATE))
                .isEqualByComparingTo(feeScheduleService.rateFor("MP101", SERVICE_DATE));
    }
}
