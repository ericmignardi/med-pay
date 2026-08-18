package com.medpay.ledger.repository;

import com.medpay.ledger.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc();

    List<OutboxEvent> findByClaimIdOrderByCreatedAtAsc(Long claimId);

    boolean existsByEventUuid(UUID eventUuid);
}
