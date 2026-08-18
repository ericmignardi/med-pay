package com.medpay.ledger.repository;

import com.medpay.ledger.model.ClaimLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimLineRepository extends JpaRepository<ClaimLine, Long> {

    List<ClaimLine> findByClaimIdOrderByLineNumberAsc(Long claimId);
}
