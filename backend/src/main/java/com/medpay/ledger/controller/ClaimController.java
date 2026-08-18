package com.medpay.ledger.controller;

import com.medpay.ledger.dto.ClaimResponse;
import com.medpay.ledger.dto.ClaimSubmissionRequest;
import com.medpay.ledger.dto.ClaimSummaryResponse;
import com.medpay.ledger.dto.PageResponse;
import com.medpay.ledger.dto.ReversalRequest;
import com.medpay.ledger.exception.MissingIdempotencyKeyException;
import com.medpay.ledger.model.ClaimStatus;
import com.medpay.ledger.security.AuthenticatedUser;
import com.medpay.ledger.service.ClaimQueryService;
import com.medpay.ledger.service.ClaimSubmissionService;
import com.medpay.ledger.service.ReversalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims")
public class ClaimController {

    private final ClaimSubmissionService submissionService;
    private final ClaimQueryService queryService;
    private final ReversalService reversalService;

    public ClaimController(ClaimSubmissionService submissionService,
                           ClaimQueryService queryService,
                           ReversalService reversalService) {
        this.submissionService = submissionService;
        this.queryService = queryService;
        this.reversalService = reversalService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CLAIMS_PROCESSOR')")
    public ResponseEntity<ClaimResponse> submit(
            @Valid @RequestBody ClaimSubmissionRequest request,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        UUID key = parseIdempotencyKey(idempotencyKey);
        ClaimSubmissionService.SubmissionOutcome outcome =
                submissionService.submit(request, key, principal);

        HttpStatus status = outcome.flagged() ? HttpStatus.ACCEPTED : HttpStatus.CREATED;

        return ResponseEntity.status(status)
                .location(URI.create("/api/v1/claims/" + outcome.claim().claimUuid()))
                .body(outcome.claim());
    }

    @GetMapping
    @PreAuthorize("hasRole('CLAIMS_PROCESSOR')")
    public ResponseEntity<PageResponse<ClaimSummaryResponse>> list(
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return ResponseEntity.ok(queryService.listOwn(principal, status, page, size));
    }

    @GetMapping("/{claimUuid}")
    @PreAuthorize("hasRole('CLAIMS_PROCESSOR')")
    public ResponseEntity<ClaimResponse> findOne(
            @PathVariable UUID claimUuid,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return ResponseEntity.ok(queryService.findOwn(principal, claimUuid));
    }

    @PostMapping("/{claimUuid}/reversals")
    @PreAuthorize("hasRole('MEDICAL_REVIEWER')")
    public ResponseEntity<ClaimResponse> reverse(
            @PathVariable UUID claimUuid,
            @Valid @RequestBody ReversalRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return ResponseEntity.ok(reversalService.reverse(claimUuid, request, principal));
    }

    private static UUID parseIdempotencyKey(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException notAUuid) {
            throw new MissingIdempotencyKeyException(
                    "The Idempotency-Key header must contain a UUID");
        }
    }
}
