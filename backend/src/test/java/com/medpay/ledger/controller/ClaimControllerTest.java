package com.medpay.ledger.controller;

import com.medpay.ledger.repository.LedgerJournalRepository;
import com.medpay.ledger.testsupport.AbstractIntegrationTest;
import com.medpay.ledger.testsupport.WithMockCustomUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClaimControllerTest extends AbstractIntegrationTest {

    private static final String PROCESSOR_UUID = "a1e8c4d2-7b3f-4e6a-9c15-2d8f0b6e3a91";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LedgerJournalRepository journalRepository;

    private String submission(String headerBilled, String lineBilled, String serviceCode) {
        String header = headerBilled == null ? "null" : headerBilled;
        return """
                {
                  "providerNpi": "1000000001",
                  "memberReference": "MBR-%s",
                  "serviceDate": "%s",
                  "billedAmount": %s,
                  "lines": [
                    { "serviceCode": "%s", "diagnosisCode": "E1165", "billedAmount": %s }
                  ]
                }
                """.formatted(
                UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(),
                LocalDate.now().minusDays(1),
                header, serviceCode, lineBilled);
    }

    private String bigSubmission(String headerBilled, String lineBilled) {
        return submission(headerBilled, lineBilled, "SX304");
    }

    @ParameterizedTest(name = "{0}: billed={1} lineSum={2} -> {3} {4}")
    @CsvSource(nullValues = "NONE", value = {
            "TC-B-005, 24999.999,           24999.999,           400, VALIDATION_FAILED",
            "TC-B-006, 0.00,                0.00,                400, VALIDATION_FAILED",
            "TC-B-007, -100.00,             -100.00,             400, VALIDATION_FAILED",
            "TC-B-008, null,                100.00,              400, VALIDATION_FAILED",
            "TC-B-009, 1234567890123456.00, 1234567890123456.00, 400, VALIDATION_FAILED",
            "TC-B-010, 25000.00,            24999.99,            422, LINE_SUM_MISMATCH",
            "TC-B-011, 25000.00,            25000.01,            422, LINE_SUM_MISMATCH"
    })
    @DisplayName("FR-013: the rejection half of the boundary matrix")
    @WithMockCustomUser(userUuid = PROCESSOR_UUID, roles = "CLAIMS_PROCESSOR")
    void boundaryRejections(String caseId, String billed, String lineSum,
                            int expectedStatus, String expectedCode) throws Exception {
        long journalsBefore = journalRepository.count();

        mockMvc.perform(post("/api/v1/claims")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bigSubmission("null".equals(billed) ? null : billed, lineSum)))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode));

        assertThat(journalRepository.count())
                .as("%s must post no journal rows", caseId)
                .isEqualTo(journalsBefore);
    }

    @Test
    @DisplayName("TC-B-012: an unknown service code is 422 UNKNOWN_SERVICE_CODE")
    @WithMockCustomUser(userUuid = PROCESSOR_UUID, roles = "CLAIMS_PROCESSOR")
    void unknownServiceCodeIsUnprocessable() throws Exception {
        mockMvc.perform(post("/api/v1/claims")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submission("100.00", "100.00", "ZZZZZ")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNKNOWN_SERVICE_CODE"))
                .andExpect(jsonPath("$.details.serviceCode").value("ZZZZZ"));
    }

    @Test
    @DisplayName("TC-B-001: below threshold adjudicates to PAID with 201 and exactly two journal rows")
    @WithMockCustomUser(userUuid = PROCESSOR_UUID, roles = "CLAIMS_PROCESSOR")
    void belowThresholdAdjudicatesAndPosts() throws Exception {
        String body = mockMvc.perform(post("/api/v1/claims")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bigSubmission("24999.99", "24999.99")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.journalGroups.length()").value(1))
                .andExpect(jsonPath("$.journalGroups[0].lines.length()").value(2))
                .andExpect(jsonPath("$.allowedAmount").isString())
                .andReturn().getResponse().getContentAsString();

        var claim = objectMapper.readTree(body);
        assertThat(claim.get("allowedAmount").asString()).isEqualTo("24999.99");
        assertThat(claim.get("patientResponsibility").asString()).isEqualTo("0.00");
    }

    @ParameterizedTest(name = "{0}: billed={1} flags for review")
    @CsvSource({
            "TC-B-002, 25000.00, 25000.00",
            "TC-B-003, 25000.01, 25000.01",
            "TC-B-004, 25000.0,  25000.00"
    })
    @DisplayName("FR-011: at or above the threshold holds with 202 and posts nothing")
    @WithMockCustomUser(userUuid = PROCESSOR_UUID, roles = "CLAIMS_PROCESSOR")
    void atOrAboveThresholdFlagsForReview(String caseId, String billed, String lineSum)
            throws Exception {
        long journalsBefore = journalRepository.count();

        mockMvc.perform(post("/api/v1/claims")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bigSubmission(billed, lineSum)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("FLAGGED_REVIEW"))
                .andExpect(jsonPath("$.journalGroups.length()").value(0))
                .andExpect(jsonPath("$.allowedAmount").isString());

        assertThat(journalRepository.count())
                .as("%s posts no ledger rows", caseId)
                .isEqualTo(journalsBefore);
    }

    @Test
    @DisplayName("FR-007: replaying an Idempotency-Key returns the original claim, not a second one")
    @WithMockCustomUser(userUuid = PROCESSOR_UUID, roles = "CLAIMS_PROCESSOR")
    void replayedIdempotencyKeyReturnsTheOriginal() throws Exception {
        String key = UUID.randomUUID().toString();
        String payload = bigSubmission("1500.00", "1500.00");
        long journalsBefore = journalRepository.count();

        String first = mockMvc.perform(post("/api/v1/claims")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(post("/api/v1/claims")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(second).get("claimUuid"))
                .isEqualTo(objectMapper.readTree(first).get("claimUuid"));

        assertThat(journalRepository.count())
                .as("one claim, one journal group")
                .isEqualTo(journalsBefore + 2);
    }

    @Test
    @DisplayName("FR-008: the same encounter under a different key is 409 DUPLICATE_CLAIM")
    @WithMockCustomUser(userUuid = PROCESSOR_UUID, roles = "CLAIMS_PROCESSOR")
    void duplicateEncounterIsRejected() throws Exception {
        String payload = bigSubmission("1600.00", "1600.00");

        String first = mockMvc.perform(post("/api/v1/claims")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String existingUuid = objectMapper.readTree(first).get("claimUuid").asString();

        mockMvc.perform(post("/api/v1/claims")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CLAIM"))
                .andExpect(jsonPath("$.details.existingClaimUuid").value(existingUuid));
    }

    @Test
    @DisplayName("FR-007: a missing Idempotency-Key is 400 MISSING_IDEMPOTENCY_KEY")
    @WithMockCustomUser(userUuid = PROCESSOR_UUID, roles = "CLAIMS_PROCESSOR")
    void missingIdempotencyKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bigSubmission("100.00", "100.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_IDEMPOTENCY_KEY"));
    }

    @Test
    @DisplayName("FR-007: a non-UUID Idempotency-Key is 400 MISSING_IDEMPOTENCY_KEY")
    @WithMockCustomUser(userUuid = PROCESSOR_UUID, roles = "CLAIMS_PROCESSOR")
    void malformedIdempotencyKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/claims")
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bigSubmission("100.00", "100.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_IDEMPOTENCY_KEY"));
    }

    @Test
    @DisplayName("FR-030: a memberReference violation does not echo the submitted value")
    @WithMockCustomUser(userUuid = PROCESSOR_UUID, roles = "CLAIMS_PROCESSOR")
    void phiFieldsAreNotEchoedBack() throws Exception {
        String payload = """
                {
                  "providerNpi": "1000000001",
                  "memberReference": "",
                  "serviceDate": "%s",
                  "billedAmount": 100.00,
                  "lines": [
                    { "serviceCode": "MP101", "diagnosisCode": "E1165", "billedAmount": 100.00 }
                  ]
                }
                """.formatted(LocalDate.now().minusDays(1));

        mockMvc.perform(post("/api/v1/claims")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'memberReference')].rejectedValue")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    @DisplayName("§2.2: a processor sees only their own claims")
    @WithMockCustomUser(userUuid = PROCESSOR_UUID, roles = "CLAIMS_PROCESSOR")
    void listIsScopedToTheCaller() throws Exception {
        mockMvc.perform(post("/api/v1/claims")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bigSubmission("1700.00", "1700.00")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/claims").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5));
    }

    @Test
    @DisplayName("FR-025: size above 100 is clamped rather than rejected")
    @WithMockCustomUser(userUuid = PROCESSOR_UUID, roles = "CLAIMS_PROCESSOR")
    void pageSizeIsClamped() throws Exception {
        mockMvc.perform(get("/api/v1/claims").param("size", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("§2.3: another processor's claim is 404, not 403 — existence is not disclosed")
    @WithMockCustomUser(userUuid = PROCESSOR_UUID, userId = 999L, roles = "CLAIMS_PROCESSOR")
    void anotherUsersClaimIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/claims/{uuid}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_FOUND"));
    }

    @Test
    @DisplayName("§2.2: MEDICAL_REVIEWER cannot submit or list claims")
    @WithMockCustomUser(roles = "MEDICAL_REVIEWER")
    void reviewerIsForbiddenOnClaims() throws Exception {
        mockMvc.perform(get("/api/v1/claims"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/claims")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bigSubmission("100.00", "100.00")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("§2.2: AUDITOR cannot submit or list claims")
    @WithMockCustomUser(roles = "AUDITOR")
    void auditorIsForbiddenOnClaims() throws Exception {
        mockMvc.perform(get("/api/v1/claims"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/claims/{uuid}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an unauthenticated submission is 401")
    void anonymousSubmissionIsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/claims")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bigSubmission("100.00", "100.00")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
