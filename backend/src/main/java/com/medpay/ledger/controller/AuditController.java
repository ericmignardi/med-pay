package com.medpay.ledger.controller;

import com.medpay.ledger.dto.ClaimAuditResponse;
import com.medpay.ledger.dto.JournalLineResponse;
import com.medpay.ledger.dto.PageResponse;
import com.medpay.ledger.service.AuditQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasRole('AUDITOR')")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/journals")
    public ResponseEntity<PageResponse<JournalLineResponse>> journals(
            @RequestParam(required = false) String providerNpi,
            @RequestParam(required = false) UUID claimUuid,
            @RequestParam(required = false) UUID journalGroupId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant postedFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant postedTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        return ResponseEntity.ok(auditQueryService.journals(
                providerNpi, claimUuid, journalGroupId, postedFrom, postedTo, page, size));
    }

    @GetMapping("/claims/{claimUuid}")
    public ResponseEntity<ClaimAuditResponse> claimHistory(@PathVariable UUID claimUuid) {
        return ResponseEntity.ok(auditQueryService.claimHistory(claimUuid));
    }
}
