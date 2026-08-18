package com.medpay.ledger.controller;

import com.medpay.ledger.dto.ClaimResponse;
import com.medpay.ledger.dto.ClaimSummaryResponse;
import com.medpay.ledger.dto.PageResponse;
import com.medpay.ledger.dto.ReviewDecisionRequest;
import com.medpay.ledger.dto.ReviewDenialRequest;
import com.medpay.ledger.security.AuthenticatedUser;
import com.medpay.ledger.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/review")
@PreAuthorize("hasRole('MEDICAL_REVIEWER')")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/queue")
    public ResponseEntity<PageResponse<ClaimSummaryResponse>> queue(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        return ResponseEntity.ok(reviewService.queue(page, size));
    }

    @GetMapping("/claims/{claimUuid}")
    public ResponseEntity<ClaimResponse> findFlagged(@PathVariable UUID claimUuid) {
        return ResponseEntity.ok(reviewService.findFlagged(claimUuid));
    }

    @PostMapping("/claims/{claimUuid}/approve")
    public ResponseEntity<ClaimResponse> approve(
            @PathVariable UUID claimUuid,
            @Valid @RequestBody(required = false) ReviewDecisionRequest request,
            @AuthenticationPrincipal AuthenticatedUser reviewer) {

        ReviewDecisionRequest decision =
                request != null ? request : new ReviewDecisionRequest(null);

        return ResponseEntity.ok(reviewService.approve(claimUuid, decision, reviewer));
    }

    @PostMapping("/claims/{claimUuid}/deny")
    public ResponseEntity<ClaimResponse> deny(
            @PathVariable UUID claimUuid,
            @Valid @RequestBody ReviewDenialRequest request,
            @AuthenticationPrincipal AuthenticatedUser reviewer) {

        return ResponseEntity.ok(reviewService.deny(claimUuid, request, reviewer));
    }
}
