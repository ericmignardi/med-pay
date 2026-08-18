package com.medpay.ledger.service;

import com.medpay.ledger.dto.ClaimLineRequest;
import com.medpay.ledger.dto.ClaimSubmissionRequest;
import com.medpay.ledger.exception.LineItemSumMismatchException;
import com.medpay.ledger.util.MoneyMath;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ClaimValidator {

    public void assertLineSumMatchesHeader(ClaimSubmissionRequest request) {
        BigDecimal lineSum = MoneyMath.sum(
                request.lines().stream().map(ClaimLineRequest::billedAmount).toList());

        if (!MoneyMath.equalToTheCent(request.billedAmount(), lineSum)) {
            throw new LineItemSumMismatchException(request.billedAmount(), lineSum);
        }
    }

    public BigDecimal lineSum(List<ClaimLineRequest> lines) {
        return MoneyMath.sum(lines.stream().map(ClaimLineRequest::billedAmount).toList());
    }
}
