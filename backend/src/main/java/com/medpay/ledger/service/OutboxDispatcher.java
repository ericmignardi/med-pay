package com.medpay.ledger.service;

import com.medpay.ledger.model.OutboxEvent;
import com.medpay.ledger.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxDispatcher {

    private static final int BATCH_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxEventRepository outboxRepository;
    private final RemittanceAdviceLogSink sink;

    public OutboxDispatcher(OutboxEventRepository outboxRepository, RemittanceAdviceLogSink sink) {
        this.outboxRepository = outboxRepository;
        this.sink = sink;
    }

    @Scheduled(
            fixedDelayString = "${app.outbox.dispatch-interval-ms:5000}",
            initialDelayString = "${app.outbox.dispatch-initial-delay-ms:5000}")
    @Transactional
    public int dispatch() {
        List<OutboxEvent> batch = outboxRepository.claimUnpublishedBatch(BATCH_SIZE);
        if (batch.isEmpty()) {
            return 0;
        }

        for (OutboxEvent event : batch) {
            sink.publish(event);
            event.setPublishedAt(Instant.now());
        }
        outboxRepository.saveAll(batch);

        log.debug("Dispatched {} outbox events", batch.size());
        return batch.size();
    }
}
