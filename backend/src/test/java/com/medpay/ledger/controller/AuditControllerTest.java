package com.medpay.ledger.controller;

import com.medpay.ledger.model.Claim;
import com.medpay.ledger.model.ClaimStatus;
import com.medpay.ledger.model.Role;
import com.medpay.ledger.security.AuthenticatedUser;
import com.medpay.ledger.testsupport.AbstractIntegrationTest;
import com.medpay.ledger.testsupport.ClaimFixtures;
import com.medpay.ledger.testsupport.WithMockCustomUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditControllerTest extends AbstractIntegrationTest {

    private static final String PROCESSOR_UUID = "a1e8c4d2-7b3f-4e6a-9c15-2d8f0b6e3a91";
    private static final String REVIEWER_UUID = "b2f9d5e3-8c4a-4f7b-8d26-3e9a1c7f4b02";
    private static final String AUDITOR_UUID = "c3a0e6f4-9d5b-4a8c-9e37-4f0b2d8a5c13";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClaimFixtures fixtures;

    private static RequestPostProcessor asReviewer() {
        return user(new AuthenticatedUser(2L, UUID.fromString(REVIEWER_UUID),
                "reviewer@medpay.test", "Dr. Marcus Oyelaran",
                Set.of(Role.MEDICAL_REVIEWER)));
    }

    /**
     * Drives a claim to PAID through the review endpoint, so the journal rows the audit
     * assertions read were posted by production code rather than fabricated by the fixture.
     */
    private Claim paidClaim() throws Exception {
        Claim claim = fixtures.persistedClaim("60000.00", "50000.00", ClaimStatus.FLAGGED_REVIEW);

        mockMvc.perform(post("/api/v1/review/claims/{uuid}/approve", claim.getClaimUuid())
                        .with(asReviewer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"approved for the audit fixture\"}"))
                .andExpect(status().isOk());

        return claim;
    }

    @Test
    @DisplayName("FR-021: the auditor sees every journal row, newest first")
    @WithMockCustomUser(userUuid = AUDITOR_UUID, userId = 3L, roles = "AUDITOR")
    void journalsAreVisibleToTheAuditor() throws Exception {
        paidClaim();

        mockMvc.perform(get("/api/v1/audit/journals").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].journalGroupId").isNotEmpty())
                .andExpect(jsonPath("$.content[0].accountType").isNotEmpty())
                .andExpect(jsonPath("$.content[0].amount").isString())
                // PRD 5.5 freezes money as a two-decimal string; storage is NUMERIC(19,4)
                // and would otherwise leak "125.0000" into the contract.
                .andExpect(jsonPath("$.content[0].amount", matchesPattern("^\\d+\\.\\d{2}$")));
    }

    @Test
    @DisplayName("FR-021: filters compose with AND and narrow to a single balanced pair")
    @WithMockCustomUser(userUuid = AUDITOR_UUID, userId = 3L, roles = "AUDITOR")
    void filtersComposeAndNarrowToOnePair() throws Exception {
        Claim claim = paidClaim();
        paidClaim();

        mockMvc.perform(get("/api/v1/audit/journals")
                        .param("claimUuid", claim.getClaimUuid().toString())
                        .param("providerNpi", ClaimFixtures.DEFAULT_NPI)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].claimUuid").value(claim.getClaimUuid().toString()))
                .andExpect(jsonPath("$.content[1].claimUuid").value(claim.getClaimUuid().toString()));
    }

    @Test
    @DisplayName("FR-021: a filter matching nothing returns an empty page, not an error")
    @WithMockCustomUser(userUuid = AUDITOR_UUID, userId = 3L, roles = "AUDITOR")
    void unmatchedFilterReturnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/v1/audit/journals")
                        .param("claimUuid", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("FR-021: an unparseable UUID filter is a 400, not a 500")
    @WithMockCustomUser(userUuid = AUDITOR_UUID, userId = 3L, roles = "AUDITOR")
    void unparseableFilterIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/audit/journals").param("claimUuid", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FR-022: the lifecycle view returns claim, lines, journal groups and events")
    @WithMockCustomUser(userUuid = AUDITOR_UUID, userId = 3L, roles = "AUDITOR")
    void claimHistoryReconstructsTheWholeLifecycle() throws Exception {
        Claim claim = paidClaim();

        mockMvc.perform(get("/api/v1/audit/claims/{uuid}", claim.getClaimUuid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.claimUuid").value(claim.getClaimUuid().toString()))
                .andExpect(jsonPath("$.claim.status").value("PAID"))
                .andExpect(jsonPath("$.claim.lines.length()").value(1))
                .andExpect(jsonPath("$.journalGroups.length()").value(1))
                .andExpect(jsonPath("$.journalGroups[0].lines.length()").value(2))
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.events[0].eventType").isNotEmpty());
    }

    @Test
    @DisplayName("FR-022: the auditor reads a claim they did not submit or review")
    @WithMockCustomUser(userUuid = AUDITOR_UUID, userId = 3L, roles = "AUDITOR")
    void auditorReadsAClaimTheyDoNotOwn() throws Exception {
        Claim claim = fixtures.persistedClaim("400.00", "320.00", ClaimStatus.PAID);

        mockMvc.perform(get("/api/v1/audit/claims/{uuid}", claim.getClaimUuid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.claimUuid").value(claim.getClaimUuid().toString()));
    }

    @Test
    @DisplayName("FR-022: an unknown claim UUID is 404 CLAIM_NOT_FOUND")
    @WithMockCustomUser(userUuid = AUDITOR_UUID, userId = 3L, roles = "AUDITOR")
    void unknownClaimIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/audit/claims/{uuid}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_FOUND"));
    }

    @Test
    @DisplayName("RBAC 2.2: CLAIMS_PROCESSOR is 403 on both audit endpoints")
    @WithMockCustomUser(userUuid = PROCESSOR_UUID, userId = 1L, roles = "CLAIMS_PROCESSOR")
    void processorIsForbiddenFromAudit() throws Exception {
        mockMvc.perform(get("/api/v1/audit/journals")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/audit/claims/{uuid}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC 2.2: MEDICAL_REVIEWER is 403 on both audit endpoints")
    @WithMockCustomUser(userUuid = REVIEWER_UUID, userId = 2L, roles = "MEDICAL_REVIEWER")
    void reviewerIsForbiddenFromAudit() throws Exception {
        mockMvc.perform(get("/api/v1/audit/journals")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/audit/claims/{uuid}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("NFR-001: an unauthenticated request is 401, never 403")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/audit/journals")).andExpect(status().isUnauthorized());
    }
}
