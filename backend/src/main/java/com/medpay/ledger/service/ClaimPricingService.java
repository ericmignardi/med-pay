package com.medpay.ledger.service;

import com.medpay.ledger.dto.ClaimLineRequest;
import com.medpay.ledger.dto.ClaimSubmissionRequest;
import com.medpay.ledger.util.MoneyMath;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ClaimPricingService {

    private final FeeScheduleService feeScheduleService;

    public ClaimPricingService(FeeScheduleService feeScheduleService) {
        this.feeScheduleService = feeScheduleService;
    }

    public ClaimPricing price(ClaimSubmissionRequest request) {
        return price(request.lines(), request.serviceDate());
    }

    public ClaimPricing price(List<ClaimLineRequest> lines, LocalDate serviceDate) {
        List<ClaimPricing.PricedLine> priced = new ArrayList<>(lines.size());

        BigDecimal allowedTotal = BigDecimal.ZERO;
        BigDecimal responsibilityTotal = BigDecimal.ZERO;

        short lineNumber = 1;
        for (ClaimLineRequest line : lines) {
            BigDecimal contractedRate = feeScheduleService.rateFor(line.serviceCode(), serviceDate);

            BigDecimal billed = MoneyMath.normalize(line.billedAmount());
            BigDecimal allowed = MoneyMath.allowedFor(billed, contractedRate);
            BigDecimal responsibility = MoneyMath.normalize(billed.subtract(allowed));

            priced.add(new ClaimPricing.PricedLine(
                    lineNumber++,
                    line.serviceCode(),
                    line.diagnosisCode(),
                    billed,
                    allowed,
                    responsibility));

            allowedTotal = allowedTotal.add(allowed);
            responsibilityTotal = responsibilityTotal.add(responsibility);
        }

        return new ClaimPricing(
                MoneyMath.normalize(allowedTotal),
                MoneyMath.normalize(responsibilityTotal),
                List.copyOf(priced));
    }
}
