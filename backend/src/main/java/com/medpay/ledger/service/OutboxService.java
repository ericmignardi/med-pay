package com.medpay.ledger.service;

import com.medpay.ledger.model.Claim;
import com.medpay.ledger.model.OutboxEvent;
import com.medpay.ledger.model.OutboxEventType;
import com.medpay.ledger.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.UUID;

@Service
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent record(Claim claim, OutboxEventType type, Map<String, Object> attributes) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("claimUuid", claim.getClaimUuid().toString());
        payload.put("eventType", type.name());
        payload.put("status", claim.getStatus().name());
        payload.put("providerNpi", claim.getProvider().getProviderNpi());
        payload.put("billedAmount", claim.getBilledAmount().toPlainString());
        if (claim.getAllowedAmount() != null) {
            payload.put("allowedAmount", claim.getAllowedAmount().toPlainString());
        }
        attributes.forEach((key, value) -> payload.put(key, value == null ? null : value.toString()));

        OutboxEvent event = new OutboxEvent();
        event.setEventUuid(UUID.randomUUID());
        event.setClaim(claim);
        event.setEventType(type);
        event.setPayload(payload.toString());

        return outboxEventRepository.save(event);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent record(Claim claim, OutboxEventType type) {
        return record(claim, type, Map.of());
    }
}
