package com.medpay.ledger.service;

import com.medpay.ledger.model.Claim;
import com.medpay.ledger.model.ClaimStatus;
import com.medpay.ledger.model.OutboxEvent;
import com.medpay.ledger.model.OutboxEventType;
import com.medpay.ledger.repository.OutboxEventRepository;
import com.medpay.ledger.testsupport.AbstractIntegrationTest;
import com.medpay.ledger.testsupport.ClaimFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-024. The dispatcher is the at-least-once delivery seam, so what matters is that
 * nothing is lost, nothing is delivered twice within a run, and a restart drains whatever
 * an interrupted run left behind.
 */
class OutboxDispatcherIT extends AbstractIntegrationTest {

    @Autowired
    private OutboxDispatcher dispatcher;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ClaimFixtures fixtures;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /** OutboxService#record is MANDATORY, so a caller must supply the transaction. */
    private OutboxEvent recordEvent(Claim claim, OutboxEventType type) {
        return transactionTemplate.execute(status ->
                outboxService.record(claim, type, Map.of("source", "OutboxDispatcherIT")));
    }

    private Claim claim() {
        return fixtures.persistedClaim("400.00", "320.00", ClaimStatus.PAID);
    }

    @Test
    @DisplayName("FR-024: dispatch stamps published_at and leaves nothing unpublished")
    void dispatchDrainsThePendingBatch() {
        recordEvent(claim(), OutboxEventType.CLAIM_PAID);
        recordEvent(claim(), OutboxEventType.CLAIM_SUBMITTED);

        assertThat(outboxEventRepository.countByPublishedAtIsNull()).isPositive();

        int dispatched = drain();

        assertThat(dispatched).isPositive();
        assertThat(outboxEventRepository.countByPublishedAtIsNull()).isZero();
    }

    @Test
    @DisplayName("FR-024: a second dispatch re-publishes nothing — published rows are not reclaimed")
    void alreadyPublishedRowsAreNotRedelivered() {
        recordEvent(claim(), OutboxEventType.CLAIM_PAID);
        drain();

        assertThat(dispatcher.dispatch()).isZero();
        assertThat(outboxEventRepository.countByPublishedAtIsNull()).isZero();
    }

    @Test
    @DisplayName("FR-024: rows written after a run are drained by the next one, as on a restart")
    void rowsWrittenAfterARunAreDrainedByTheNext() {
        drain();

        OutboxEvent stranded = recordEvent(claim(), OutboxEventType.CLAIM_REVERSED);
        assertThat(outboxEventRepository.findById(stranded.getId()).orElseThrow()
                .getPublishedAt()).isNull();

        // A fresh dispatch is exactly what a restarted scheduler does.
        assertThat(dispatcher.dispatch()).isPositive();
        assertThat(outboxEventRepository.findById(stranded.getId()).orElseThrow()
                .getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("FR-024: SKIP LOCKED means two dispatchers split the batch and never double-claim")
    void concurrentDispatchersDoNotClaimTheSameRow() throws Exception {
        drain();

        int pending = 8;
        for (int i = 0; i < pending; i++) {
            recordEvent(claim(), OutboxEventType.CLAIM_PAID);
        }
        assertThat(outboxEventRepository.countByPublishedAtIsNull()).isEqualTo(pending);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Integer> run = () -> {
                start.await();
                return dispatcher.dispatch();
            };
            List<Future<Integer>> futures = List.of(pool.submit(run), pool.submit(run));

            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

            int totalClaimed = futures.stream().mapToInt(future -> {
                try {
                    return future.get();
                } catch (Exception e) {
                    return 0;
                }
            }).sum();

            // No row is counted twice: the two runs together claim exactly what was pending.
            assertThat(totalClaimed).isEqualTo(pending);
        } finally {
            pool.shutdownNow();
        }

        assertThat(outboxEventRepository.countByPublishedAtIsNull()).isZero();
    }

    @Test
    @DisplayName("FR-024: every event carries a distinct UUID so the sink can de-duplicate a replay")
    void eventsCarryDistinctUuids() {
        Claim claim = claim();
        UUID first = recordEvent(claim, OutboxEventType.CLAIM_SUBMITTED).getEventUuid();
        UUID second = recordEvent(claim, OutboxEventType.CLAIM_PAID).getEventUuid();

        assertThat(first).isNotEqualTo(second);
        assertThat(outboxEventRepository.existsByEventUuid(first)).isTrue();
        assertThat(outboxEventRepository.existsByEventUuid(second)).isTrue();
    }

    /** Loops until the queue is empty — one call only claims up to the batch size. */
    private int drain() {
        int total = 0;
        for (int pass = 0; pass < 20; pass++) {
            int dispatched = dispatcher.dispatch();
            total += dispatched;
            if (dispatched == 0) {
                break;
            }
        }
        return total;
    }
}
