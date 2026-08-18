package com.medpay.ledger.service;

import com.medpay.ledger.model.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class RemittanceAdviceLogSink {

    private static final Logger log = LoggerFactory.getLogger(RemittanceAdviceLogSink.class);

    private final AtomicLong published = new AtomicLong();

    public void publish(OutboxEvent event) {
        published.incrementAndGet();
        log.info("remittance-advice eventUuid={} eventType={} claimId={} createdAt={}",
                event.getEventUuid(), event.getEventType(),
                event.getClaim().getId(), event.getCreatedAt());
    }

    public long publishedCount() {
        return published.get();
    }
}
