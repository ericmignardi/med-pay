package com.medpay.ledger.service;

import com.medpay.ledger.dto.FeeScheduleResponse;
import com.medpay.ledger.exception.UnknownServiceCodeException;
import com.medpay.ledger.model.FeeSchedule;
import com.medpay.ledger.repository.FeeScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class FeeScheduleService {

    private final FeeScheduleRepository feeScheduleRepository;

    public FeeScheduleService(FeeScheduleRepository feeScheduleRepository) {
        this.feeScheduleRepository = feeScheduleRepository;
    }

    public BigDecimal rateFor(String serviceCode, LocalDate effectiveOn) {
        String canonical = serviceCode.trim().toUpperCase(Locale.ROOT);
        return feeScheduleRepository.findRateFor(canonical, effectiveOn)
                .map(FeeSchedule::getContractedRate)
                .orElseThrow(() -> new UnknownServiceCodeException(canonical, effectiveOn));
    }

    public List<FeeScheduleResponse> effectiveOn(LocalDate onDate) {
        return feeScheduleRepository.findAllEffectiveOn(onDate).stream()
                .map(fee -> new FeeScheduleResponse(
                        fee.getServiceCode(),
                        fee.getDescription(),
                        fee.getContractedRate(),
                        fee.getEffectiveFrom(),
                        fee.getEffectiveTo()))
                .toList();
    }
}
