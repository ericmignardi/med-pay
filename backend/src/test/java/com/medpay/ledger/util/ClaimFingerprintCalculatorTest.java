package com.medpay.ledger.util;

import com.medpay.ledger.dto.ClaimLineRequest;
import com.medpay.ledger.dto.ClaimSubmissionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimFingerprintCalculatorTest {

    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 3, 14);

    private final ClaimFingerprintCalculator calculator = new ClaimFingerprintCalculator();

    private ClaimSubmissionRequest request(String npi, String member, LocalDate date,
                                           List<String> codes) {
        List<ClaimLineRequest> lines = codes.stream()
                .map(code -> new ClaimLineRequest(code, "E1165", new BigDecimal("100.00")))
                .toList();
        return new ClaimSubmissionRequest(npi, member, date, new BigDecimal("100.00"), lines);
    }

    @Test
    @DisplayName("FR-008: line order does not change the digest")
    void fingerprintIsOrderIndependent() {
        String forward = calculator.fingerprint(
                request("1000000001", "MBR-7HK92QX4TR10", SERVICE_DATE,
                        List.of("MP101", "DX201", "SX301")));

        String reversed = calculator.fingerprint(
                request("1000000001", "MBR-7HK92QX4TR10", SERVICE_DATE,
                        List.of("SX301", "DX201", "MP101")));

        assertThat(forward).isEqualTo(reversed);
    }

    @Test
    @DisplayName("FR-008: a repeated service code collapses, so duplication does not evade detection")
    void repeatedCodesAreDeduplicated() {
        String once = calculator.fingerprint(
                request("1000000001", "MBR-A", SERVICE_DATE, List.of("MP101", "DX201")));

        String twice = calculator.fingerprint(
                request("1000000001", "MBR-A", SERVICE_DATE, List.of("MP101", "DX201", "MP101")));

        assertThat(once).isEqualTo(twice);
    }

    @Test
    @DisplayName("case and surrounding whitespace are canonicalized away")
    void canonicalizationIgnoresCaseAndPadding() {
        String canonical = calculator.fingerprint(
                request("1000000001", "MBR-A", SERVICE_DATE, List.of("MP101")));

        String noisy = calculator.fingerprint(
                request("1000000001", "  MBR-A  ", SERVICE_DATE, List.of("mp101")));

        assertThat(canonical).isEqualTo(noisy);
    }

    @Test
    @DisplayName("each component of the natural key changes the digest")
    void everyKeyComponentIsSignificant() {
        String base = calculator.fingerprint(
                request("1000000001", "MBR-A", SERVICE_DATE, List.of("MP101")));

        assertThat(base).isNotEqualTo(calculator.fingerprint(
                request("1000000002", "MBR-A", SERVICE_DATE, List.of("MP101"))));
        assertThat(base).isNotEqualTo(calculator.fingerprint(
                request("1000000001", "MBR-B", SERVICE_DATE, List.of("MP101"))));
        assertThat(base).isNotEqualTo(calculator.fingerprint(
                request("1000000001", "MBR-A", SERVICE_DATE.minusDays(1), List.of("MP101"))));
        assertThat(base).isNotEqualTo(calculator.fingerprint(
                request("1000000001", "MBR-A", SERVICE_DATE, List.of("MP102"))));
    }

    @Test
    @DisplayName("the digest is 64 lowercase hex characters, matching CHAR(64)")
    void digestIsLowercaseHexOfExpectedWidth() {
        String digest = calculator.fingerprint(
                request("1000000001", "MBR-A", SERVICE_DATE, List.of("MP101")));

        assertThat(digest).hasSize(64).matches("^[0-9a-f]{64}$");
    }
}
