package com.medpay.ledger.controller;

import com.medpay.ledger.testsupport.AbstractIntegrationTest;
import com.medpay.ledger.testsupport.WithMockCustomUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeeScheduleControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("§2.2: CLAIMS_PROCESSOR may read the fee schedule")
    @WithMockCustomUser(roles = "CLAIMS_PROCESSOR")
    void processorIsAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/fee-schedules").param("effectiveOn", "2026-03-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(15))
                .andExpect(jsonPath("$[0].serviceCode").value("DX201"));
    }

    @Test
    @DisplayName("§5.2: contractedRate crosses the wire as a decimal string, not a JSON number")
    @WithMockCustomUser(roles = "CLAIMS_PROCESSOR")
    void moneyIsSerializedAsAString() throws Exception {
        mockMvc.perform(get("/api/v1/fee-schedules").param("effectiveOn", "2026-03-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contractedRate").isString());
    }

    @Test
    @DisplayName("FR-010: the effective date selects the rate in force, not the newest row")
    @WithMockCustomUser(roles = "CLAIMS_PROCESSOR")
    void effectiveDateSelectsTheCorrectRate() throws Exception {
        mockMvc.perform(get("/api/v1/fee-schedules").param("effectiveOn", "2022-06-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.serviceCode == 'RT501')].contractedRate")
                        .value("780.0000"));

        mockMvc.perform(get("/api/v1/fee-schedules").param("effectiveOn", "2023-06-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.serviceCode == 'RT501')].contractedRate")
                        .value("845.0000"));
    }

    @Test
    @DisplayName("§2.2: MEDICAL_REVIEWER is forbidden")
    @WithMockCustomUser(roles = "MEDICAL_REVIEWER")
    void reviewerIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/fee-schedules"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("§2.2: AUDITOR is forbidden")
    @WithMockCustomUser(roles = "AUDITOR")
    void auditorIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/fee-schedules"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("an unauthenticated request is 401")
    void anonymousIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/fee-schedules"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("an unparseable effectiveOn is 400 VALIDATION_FAILED, not 500")
    @WithMockCustomUser(roles = "CLAIMS_PROCESSOR")
    void unparseableDateIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/fee-schedules").param("effectiveOn", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
