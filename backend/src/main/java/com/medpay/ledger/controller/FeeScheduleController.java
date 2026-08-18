package com.medpay.ledger.controller;

import com.medpay.ledger.dto.FeeScheduleResponse;
import com.medpay.ledger.service.FeeScheduleService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/fee-schedules")
public class FeeScheduleController {

    private final FeeScheduleService feeScheduleService;

    public FeeScheduleController(FeeScheduleService feeScheduleService) {
        this.feeScheduleService = feeScheduleService;
    }

    @GetMapping
    @PreAuthorize("hasRole('CLAIMS_PROCESSOR')")
    public ResponseEntity<List<FeeScheduleResponse>> effectiveOn(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveOn) {

        LocalDate onDate = effectiveOn != null ? effectiveOn : LocalDate.now();
        return ResponseEntity.ok(feeScheduleService.effectiveOn(onDate));
    }
}
