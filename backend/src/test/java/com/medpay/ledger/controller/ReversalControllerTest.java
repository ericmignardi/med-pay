package com.medpay.ledger.controller;

import com.medpay.ledger.model.Claim;
import com.medpay.ledger.model.ClaimStatus;
import com.medpay.ledger.repository.LedgerJournalRepository;
import com.medpay.ledger.repository.ProviderAccountRepository;
import com.medpay.ledger.service.LedgerPostingService;
import com.medpay.ledger.testsupport.AbstractIntegrationTest;
import com.medpay.ledger.testsupport.ClaimFixtures;
import com.medpay.ledger.testsupport.WithMockCustomUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReversalControllerTest extends AbstractIntegrationTest {

    private static final String REVIEWER_UUID = "b2f9d5e3-8c4a-4f7b-8d26-3e9a1c7f4b02";
    private static final String BODY =
            "{\"reason\":\"DUPLICATE_PAYMENT\",\"note\":\"paid twice under two encounters\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClaimFixtures fixtures;

    @Autowired
    private LedgerPostingService ledgerPostingService;

    @Autowired
    private LedgerJournalRepository journalRepository;

    @Autowired
    private ProviderAccountRepository providerAccountRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Claim paidClaimWithPostedLedger(String npi) {
        Claim claim = fixtures.persistedClaim(
                fixtures.processor(), fixtures.provider(npi), "900.00", "700.00", ClaimStatus.PAID);
        transactionTemplate.execute(status -> ledgerPostingService.postAdjudication(claim));
        return claim;
    }

    @Test
    @DisplayName("FR-020: reversing a PAID claim posts a compensating pair and reaches REVERSED")
    @WithMockCustomUser(userUuid = REVIEWER_UUID, userId = 2L, roles = "MEDICAL_REVIEWER")
    void reversalPostsACompensatingPair() throws Exception {
        Claim claim = paidClaimWithPostedLedger("1000000004");
        BigDecimal balanceBefore = providerAccountRepository
                .findByProviderNpi("1000000004").orElseThrow().getPayableBalance();

        mockMvc.perform(post("/api/v1/claims/{uuid}/reversals", claim.getClaimUuid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVERSED"))
                .andExpect(jsonPath("$.journalGroups.length()").value(2))
                .andExpect(jsonPath("$.journalGroups[1].reversesJournalGroupId").isNotEmpty());

        assertThat(providerAccountRepository.findByProviderNpi("1000000004")
                .orElseThrow().getPayableBalance())
                .isEqualByComparingTo(balanceBefore.subtract(new BigDecimal("700.00")));

        assertThat(journalRepository.findUnbalancedJournalGroups()).isEmpty();
    }

    @Test
    @DisplayName("FR-020: reversing twice is 409 — reversal is not idempotent by design")
    @WithMockCustomUser(userUuid = REVIEWER_UUID, userId = 2L, roles = "MEDICAL_REVIEWER")
    void reversingTwiceIsAConflict() throws Exception {
        Claim claim = paidClaimWithPostedLedger("1000000005");

        mockMvc.perform(post("/api/v1/claims/{uuid}/reversals", claim.getClaimUuid())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/claims/{uuid}/reversals", claim.getClaimUuid())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"))
                .andExpect(jsonPath("$.details.currentStatus").value("REVERSED"));
    }

    @Test
    @DisplayName("FR-020: reversing a FLAGGED_REVIEW claim is 409, not a 500")
    @WithMockCustomUser(userUuid = REVIEWER_UUID, userId = 2L, roles = "MEDICAL_REVIEWER")
    void reversingAFlaggedClaimIsAConflict() throws Exception {
        Claim flagged = fixtures.persistedClaim("60000.00", "50000.00", ClaimStatus.FLAGGED_REVIEW);

        mockMvc.perform(post("/api/v1/claims/{uuid}/reversals", flagged.getClaimUuid())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"))
                .andExpect(jsonPath("$.details.currentStatus").value("FLAGGED_REVIEW"));
    }

    @Test
    @DisplayName("FR-020: reversing a DENIED claim is 409")
    @WithMockCustomUser(userUuid = REVIEWER_UUID, userId = 2L, roles = "MEDICAL_REVIEWER")
    void reversingADeniedClaimIsAConflict() throws Exception {
        Claim denied = fixtures.persistedClaim("60000.00", "50000.00", ClaimStatus.DENIED);

        mockMvc.perform(post("/api/v1/claims/{uuid}/reversals", denied.getClaimUuid())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("FR-020: a reason and a note are both mandatory")
    @WithMockCustomUser(userUuid = REVIEWER_UUID, userId = 2L, roles = "MEDICAL_REVIEWER")
    void reasonAndNoteAreMandatory() throws Exception {
        Claim claim = paidClaimWithPostedLedger("1000000006");

        mockMvc.perform(post("/api/v1/claims/{uuid}/reversals", claim.getClaimUuid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"missing reason\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/claims/{uuid}/reversals", claim.getClaimUuid())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"PROVIDER_REFUND\",\"note\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("§2.2: CLAIMS_PROCESSOR may not reverse — the originator does not unwind payment")
    @WithMockCustomUser(roles = "CLAIMS_PROCESSOR")
    void processorMayNotReverse() throws Exception {
        mockMvc.perform(post("/api/v1/claims/{uuid}/reversals", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("§2.2: AUDITOR may not reverse — the role is read-only without exception")
    @WithMockCustomUser(roles = "AUDITOR")
    void auditorMayNotReverse() throws Exception {
        mockMvc.perform(post("/api/v1/claims/{uuid}/reversals", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("reversing an unknown claim is 404")
    @WithMockCustomUser(userUuid = REVIEWER_UUID, userId = 2L, roles = "MEDICAL_REVIEWER")
    void reversingAnUnknownClaimIsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/claims/{uuid}/reversals", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_FOUND"));
    }
}
