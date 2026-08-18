package com.medpay.ledger.controller;

import com.medpay.ledger.model.Claim;
import com.medpay.ledger.model.ClaimStatus;
import com.medpay.ledger.repository.ClaimRepository;
import com.medpay.ledger.repository.LedgerJournalRepository;
import com.medpay.ledger.testsupport.AbstractIntegrationTest;
import com.medpay.ledger.testsupport.ClaimFixtures;
import com.medpay.ledger.testsupport.WithMockCustomUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewControllerTest extends AbstractIntegrationTest {

    private static final String PROCESSOR_UUID = "a1e8c4d2-7b3f-4e6a-9c15-2d8f0b6e3a91";
    private static final String REVIEWER_UUID = "b2f9d5e3-8c4a-4f7b-8d26-3e9a1c7f4b02";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClaimFixtures fixtures;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private LedgerJournalRepository journalRepository;

    private Claim flaggedClaim() {
        return fixtures.persistedClaim("60000.00", "50000.00", ClaimStatus.FLAGGED_REVIEW);
    }

    @Test
    @DisplayName("FR-016: the queue returns flagged claims, oldest first")
    @WithMockCustomUser(userUuid = REVIEWER_UUID, userId = 2L, roles = "MEDICAL_REVIEWER")
    void queueReturnsFlaggedClaims() throws Exception {
        flaggedClaim();

        mockMvc.perform(get("/api/v1/review/queue").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].status").value("FLAGGED_REVIEW"));
    }

    @Test
    @DisplayName("FR-017: approval posts the balanced pair and moves the claim to PAID")
    @WithMockCustomUser(userUuid = REVIEWER_UUID, userId = 2L, roles = "MEDICAL_REVIEWER")
    void approvePostsTheLedgerPairAndPays() throws Exception {
        Claim claim = flaggedClaim();
        long journalsBefore = journalRepository.count();

        mockMvc.perform(post("/api/v1/review/claims/{uuid}/approve", claim.getClaimUuid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"clinically appropriate\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.reviewNote").value("clinically appropriate"))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty())
                .andExpect(jsonPath("$.journalGroups.length()").value(1))
                .andExpect(jsonPath("$.journalGroups[0].lines.length()").value(2));

        assertThat(journalRepository.count()).isEqualTo(journalsBefore + 2);
    }

    @Test
    @DisplayName("FR-018: denial records a reason, writes no ledger rows, and reaches DENIED")
    @WithMockCustomUser(userUuid = REVIEWER_UUID, userId = 2L, roles = "MEDICAL_REVIEWER")
    void denyRecordsAReasonAndPostsNothing() throws Exception {
        Claim claim = flaggedClaim();
        long journalsBefore = journalRepository.count();

        mockMvc.perform(post("/api/v1/review/claims/{uuid}/deny", claim.getClaimUuid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"NOT_MEDICALLY_NECESSARY","note":"no supporting documentation"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DENIED"))
                .andExpect(jsonPath("$.denialReason").value("NOT_MEDICALLY_NECESSARY"))
                .andExpect(jsonPath("$.journalGroups.length()").value(0));

        assertThat(journalRepository.count()).isEqualTo(journalsBefore);
    }

    @Test
    @DisplayName("FR-018: a denial without a note is rejected — a denial must be justified")
    @WithMockCustomUser(userUuid = REVIEWER_UUID, userId = 2L, roles = "MEDICAL_REVIEWER")
    void denialRequiresANote() throws Exception {
        Claim claim = flaggedClaim();

        mockMvc.perform(post("/api/v1/review/claims/{uuid}/deny", claim.getClaimUuid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OUT_OF_NETWORK\",\"note\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("TC-R-004: both roles, own submission, still 409 SELF_APPROVAL_FORBIDDEN")
    @WithMockCustomUser(userUuid = PROCESSOR_UUID, userId = 1L,
            roles = {"CLAIMS_PROCESSOR", "MEDICAL_REVIEWER"})
    void approvingOwnSubmissionWithBothRolesIsForbidden() throws Exception {
        Claim claim = flaggedClaim();
        long journalsBefore = journalRepository.count();

        mockMvc.perform(post("/api/v1/review/claims/{uuid}/approve", claim.getClaimUuid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"approving my own\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_APPROVAL_FORBIDDEN"))
                .andExpect(jsonPath("$.details.claimUuid").value(claim.getClaimUuid().toString()));

        assertThat(journalRepository.count())
                .as("a blocked self-approval posts nothing")
                .isEqualTo(journalsBefore);
        assertThat(claimRepository.findByClaimUuid(claim.getClaimUuid()).orElseThrow().getStatus())
                .isEqualTo(ClaimStatus.FLAGGED_REVIEW);
    }

    @Test
    @DisplayName("FR-019: the submitter may not deny their own claim either")
    @WithMockCustomUser(userUuid = PROCESSOR_UUID, userId = 1L,
            roles = {"CLAIMS_PROCESSOR", "MEDICAL_REVIEWER"})
    void denyingOwnSubmissionIsForbidden() throws Exception {
        Claim claim = flaggedClaim();

        mockMvc.perform(post("/api/v1/review/claims/{uuid}/deny", claim.getClaimUuid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OUT_OF_NETWORK\",\"note\":\"denying my own\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_APPROVAL_FORBIDDEN"));
    }

    @Test
    @DisplayName("FR-019: the self-approval check runs before the state machine")
    @WithMockCustomUser(userUuid = PROCESSOR_UUID, userId = 1L,
            roles = {"CLAIMS_PROCESSOR", "MEDICAL_REVIEWER"})
    void selfApprovalOutranksAnIllegalTransition() throws Exception {
        Claim ownPaidClaim = fixtures.persistedClaim("100.00", "80.00", ClaimStatus.PAID);

        mockMvc.perform(post("/api/v1/review/claims/{uuid}/approve", ownPaidClaim.getClaimUuid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"already paid and mine\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_APPROVAL_FORBIDDEN"));
    }

    @Test
    @DisplayName("FR-012: approving an already-PAID claim is 409 ILLEGAL_STATE_TRANSITION")
    @WithMockCustomUser(userUuid = REVIEWER_UUID, userId = 2L, roles = "MEDICAL_REVIEWER")
    void approvingAPaidClaimIsIllegal() throws Exception {
        Claim paid = fixtures.persistedClaim("100.00", "80.00", ClaimStatus.PAID);

        mockMvc.perform(post("/api/v1/review/claims/{uuid}/approve", paid.getClaimUuid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"))
                .andExpect(jsonPath("$.details.currentStatus").value("PAID"))
                .andExpect(jsonPath("$.details.attemptedEvent").value("REVIEWER_APPROVE"));
    }

    @Test
    @DisplayName("FR-012: denying an already-DENIED claim is 409")
    @WithMockCustomUser(userUuid = REVIEWER_UUID, userId = 2L, roles = "MEDICAL_REVIEWER")
    void denyingADeniedClaimIsIllegal() throws Exception {
        Claim denied = fixtures.persistedClaim("60000.00", "50000.00", ClaimStatus.DENIED);

        mockMvc.perform(post("/api/v1/review/claims/{uuid}/deny", denied.getClaimUuid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OUT_OF_NETWORK\",\"note\":\"again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("§5.4: a claim outside FLAGGED_REVIEW is 404 to the reviewer's read scope")
    @WithMockCustomUser(userUuid = REVIEWER_UUID, userId = 2L, roles = "MEDICAL_REVIEWER")
    void reviewerReadScopeIsTheQueue() throws Exception {
        Claim paid = fixtures.persistedClaim("100.00", "80.00", ClaimStatus.PAID);

        mockMvc.perform(get("/api/v1/review/claims/{uuid}", paid.getClaimUuid()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/review/claims/{uuid}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§2.2: CLAIMS_PROCESSOR is 403 on every review endpoint")
    @WithMockCustomUser(userUuid = PROCESSOR_UUID, roles = "CLAIMS_PROCESSOR")
    void processorIsForbiddenOnReview() throws Exception {
        UUID uuid = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/review/queue"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/review/claims/{uuid}", uuid))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/review/claims/{uuid}/approve", uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"looks fine\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/review/claims/{uuid}/deny", uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"OUT_OF_NETWORK\",\"note\":\"no\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("§2.2: AUDITOR is 403 on every review endpoint")
    @WithMockCustomUser(roles = "AUDITOR")
    void auditorIsForbiddenOnReview() throws Exception {
        mockMvc.perform(get("/api/v1/review/queue"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/review/claims/{uuid}/approve", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"n\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an unauthenticated review request is 401")
    void anonymousReviewIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/review/queue"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
