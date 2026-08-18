package com.medpay.ledger.repository;

import com.medpay.ledger.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc();

    List<OutboxEvent> findByClaimIdOrderByCreatedAtAsc(Long claimId);

    List<OutboxEvent> findByClaimClaimUuidOrderByCreatedAtAscIdAsc(UUID claimUuid);

    boolean existsByEventUuid(UUID eventUuid);

    long countByPublishedAtIsNull();

    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> claimUnpublishedBatch(@Param("limit") int limit);
}
