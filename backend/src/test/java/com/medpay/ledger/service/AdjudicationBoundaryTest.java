package com.medpay.ledger.service;

import com.medpay.ledger.dto.ClaimLineRequest;
import com.medpay.ledger.dto.ClaimSubmissionRequest;
import com.medpay.ledger.exception.LineItemSumMismatchException;
import com.medpay.ledger.util.AdjudicationPolicy;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdjudicationBoundaryTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private final ClaimValidator claimValidator = new ClaimValidator();

    @BeforeAll
    static void startValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void stopValidator() {
        validatorFactory.close();
    }

    private ClaimSubmissionRequest submission(String headerBilled, String lineBilled) {
        return new ClaimSubmissionRequest(
                "1000000001",
                "MBR-7HK92QX4TR10",
                LocalDate.now().minusDays(1),
                headerBilled == null ? null : new BigDecimal(headerBilled),
                List.of(new ClaimLineRequest("MP101", "E1165", new BigDecimal(lineBilled))));
    }

    @ParameterizedTest(name = "{0}: billedAmount={1} is rejected by Bean Validation")
    @CsvSource({
            "TC-B-005, 24999.999,            24999.999",
            "TC-B-006, 0.00,                 0.00",
            "TC-B-007, -100.00,              -100.00",
            "TC-B-009, 1234567890123456.00,  1234567890123456.00"
    })
    @DisplayName("FR-013: out-of-range amounts never reach the engine")
    void amountsRejectedAtTheHttpBoundary(String caseId, String header, String line) {
        var violations = validator.validate(submission(header, line));

        assertThat(violations)
                .as("%s must fail before adjudication", caseId)
                .isNotEmpty();
        assertThat(violations)
                .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).contains("billedAmount"));
    }

    @Test
    @DisplayName("TC-B-008: a null billedAmount fails @NotNull")
    void nullBilledAmountIsRejected() {
        var violations = validator.validate(submission(null, "100.00"));

        assertThat(violations)
                .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("billedAmount"));
    }

    @ParameterizedTest(name = "{0}: header={1} lines={2} -> LINE_SUM_MISMATCH")
    @CsvSource({
            "TC-B-010, 25000.00, 24999.99",
            "TC-B-011, 25000.00, 25000.01"
    })
    @DisplayName("FR-009: a mismatch of one cent in either direction is rejected")
    void lineSumMismatchIsRejected(String caseId, String header, String line) {
        ClaimSubmissionRequest request = submission(header, line);

        assertThat(validator.validate(request))
                .as("%s is syntactically valid — the failure is semantic", caseId)
                .isEmpty();

        assertThatThrownBy(() -> claimValidator.assertLineSumMatchesHeader(request))
                .isInstanceOf(LineItemSumMismatchException.class)
                .satisfies(thrown -> {
                    var mismatch = (LineItemSumMismatchException) thrown;
                    assertThat(mismatch.getHeaderAmount()).isEqualByComparingTo(header);
                    assertThat(mismatch.getLineSum()).isEqualByComparingTo(line);
                    assertThat(mismatch.difference()).isEqualByComparingTo("0.01");
                });
    }

    @Test
    @DisplayName("FR-009: the line-sum check precedes the fee-schedule lookup")
    void lineSumIsCheckedBeforeAnyRateLookup() {
        ClaimSubmissionRequest unbalancedWithUnknownCode = new ClaimSubmissionRequest(
                "1000000001",
                "MBR-7HK92QX4TR10",
                LocalDate.now().minusDays(1),
                new BigDecimal("25000.00"),
                List.of(new ClaimLineRequest("ZZZZZ", "E1165", new BigDecimal("24999.99"))));

        assertThatThrownBy(() ->
                claimValidator.assertLineSumMatchesHeader(unbalancedWithUnknownCode))
                .isInstanceOf(LineItemSumMismatchException.class);
    }

    @ParameterizedTest(name = "{0}: billedAmount={1} sums correctly and passes validation")
    @CsvSource({
            "TC-B-001, 24999.99",
            "TC-B-002, 25000.00",
            "TC-B-003, 25000.01",
            "TC-B-004, 25000.0"
    })
    @DisplayName("FR-013: in-range amounts pass both gates")
    void wellFormedAmountsPass(String caseId, String amount) {
        ClaimSubmissionRequest request = submission(amount, amount);

        assertThat(validator.validate(request)).as(caseId).isEmpty();
        assertThatCode(() -> claimValidator.assertLineSumMatchesHeader(request))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FR-011: exactly 25000.00 holds, and 25000.0 behaves identically")
    void thresholdHoldsAtExactlyTwentyFiveThousand() {
        assertThat(AdjudicationPolicy.isBelowThreshold(new BigDecimal("24999.99"))).isTrue();
        assertThat(AdjudicationPolicy.isBelowThreshold(new BigDecimal("25000.00"))).isFalse();
        assertThat(AdjudicationPolicy.isBelowThreshold(new BigDecimal("25000.0"))).isFalse();
        assertThat(AdjudicationPolicy.isBelowThreshold(new BigDecimal("25000"))).isFalse();
        assertThat(AdjudicationPolicy.isBelowThreshold(new BigDecimal("25000.01"))).isFalse();
    }

    @Test
    @DisplayName("FR-006: a malformed service code and an over-long line list are rejected")
    void lineLevelConstraintsAreEnforced() {
        ClaimSubmissionRequest badCode = new ClaimSubmissionRequest(
                "1000000001", "MBR-A", LocalDate.now(), new BigDecimal("100.00"),
                List.of(new ClaimLineRequest("99a13", "E1165", new BigDecimal("100.00"))));

        assertThat(validator.validate(badCode))
                .anySatisfy(v -> assertThat(v.getPropertyPath().toString())
                        .isEqualTo("lines[0].serviceCode"));

        ClaimSubmissionRequest tooManyLines = new ClaimSubmissionRequest(
                "1000000001", "MBR-A", LocalDate.now(), new BigDecimal("100.00"),
                java.util.Collections.nCopies(21,
                        new ClaimLineRequest("MP101", "E1165", new BigDecimal("100.00"))));

        assertThat(validator.validate(tooManyLines))
                .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("lines"));
    }

    @Test
    @DisplayName("FR-006: a future service date and a malformed NPI are rejected")
    void headerConstraintsAreEnforced() {
        ClaimSubmissionRequest future = new ClaimSubmissionRequest(
                "12345", "MBR-A", LocalDate.now().plusDays(1), new BigDecimal("100.00"),
                List.of(new ClaimLineRequest("MP101", "E1165", new BigDecimal("100.00"))));

        assertThat(validator.validate(future))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("serviceDate", "providerNpi");
    }
}
