package com.marianna.gateway.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marianna.gateway.domain.FraudSignal;
import com.marianna.gateway.domain.PaymentEvent;
import com.marianna.gateway.domain.PaymentEventType;
import com.marianna.gateway.domain.PaymentOrder;
import com.marianna.gateway.domain.PaymentStatus;
import com.marianna.gateway.port.OutboxRepository;
import com.marianna.gateway.port.PaymentRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PaymentFinalizationService {

    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PaymentFinalizationService(PaymentRepository paymentRepository,
            OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Method in service class so it gets a real proxy and a real transaction.
     * Within that transaction, two things happen: the status update to
     * payment_orders and the insert to outbox_events. Both or neither.
     */
    @Transactional
    public PaymentOrder finalizePaymentStatus(PaymentOrder saved, FraudSignal fraudSignal) {
        PaymentOrder finalized = applyFraudDecision(saved, fraudSignal);
        paymentRepository.save(finalized);
        String payload = serialize(finalized);
        outboxRepository
                .save(new PaymentEvent(UUID.randomUUID(), finalized.id(), eventTypeForStatus(finalized.status()),
                        payload, Instant.now()));
        return finalized;
    }

    private PaymentOrder applyFraudDecision(PaymentOrder saved, FraudSignal signal) {

        if (signal.shouldDecline()) {
            log.debug("Customer: {} | payment id: {}, DECLINED", saved.customerId(), saved.id());
            return saved.withStatus(PaymentStatus.DECLINED); // DECLINE: score >= 70
        }

        if (signal.riskScore() >= 40) {
            log.debug("Customer: {} | payment id: {}, REVIEW", saved.customerId(), saved.id());
            return saved.withStatus(PaymentStatus.PROCESSING); // REVIEW: 40 <= score < 70
        }

        log.debug("Customer: {} | payment id: {}, CLEAN", saved.customerId(), saved.id());
        return saved.withStatus(PaymentStatus.COMPLETED);
    }

    private String serialize(PaymentOrder order) {
        try {
            return objectMapper.writeValueAsString(order);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize PaymentOrder id=" + order.id(), e);
        }
    }

    private PaymentEventType eventTypeForStatus(PaymentStatus status) {
        return switch (status) {
            case COMPLETED -> PaymentEventType.PAYMENT_COMPLETED;
            case DECLINED -> PaymentEventType.PAYMENT_DECLINED;
            case PROCESSING -> PaymentEventType.PAYMENT_PROCESSING;
            default -> throw new IllegalArgumentException(
                    "No eventType for PaymentStatus: " + status);
        };
    }

}
