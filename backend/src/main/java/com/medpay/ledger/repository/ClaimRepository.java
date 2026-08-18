package com.medpay.ledger.repository;

import com.medpay.ledger.model.Claim;
import com.medpay.ledger.model.ClaimStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, Long>, JpaSpecificationExecutor<Claim> {

    Optional<Claim> findByClaimUuid(UUID claimUuid);

    @EntityGraph(attributePaths = "lines")
    Optional<Claim> findWithLinesByClaimUuid(UUID claimUuid);

    Page<Claim> findBySubmittedByIdOrderBySubmittedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "lines")
    Page<Claim> findByStatusOrderBySubmittedAtAsc(ClaimStatus status, Pageable pageable);

    Optional<Claim> findBySubmittedByIdAndIdempotencyKey(Long userId, UUID idempotencyKey);

    @Query("""
            SELECT c FROM Claim c
            WHERE c.claimFingerprint = :fingerprint
              AND c.status NOT IN (com.medpay.ledger.model.ClaimStatus.DENIED,
                                   com.medpay.ledger.model.ClaimStatus.REVERSED)
            """)
    Optional<Claim> findActiveByFingerprint(@Param("fingerprint") String fingerprint);
}
