package com.medpay.ledger.dto;

import java.util.List;

public record ClaimAuditResponse(
        ClaimResponse claim,
        List<JournalGroupResponse> journalGroups,
        List<ClaimEventResponse> events) {
}
