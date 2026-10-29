package com.marianna.gateway.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import com.marianna.gateway.domain.PaymentEvent;
import com.marianna.gateway.domain.PaymentEventType;
import com.marianna.gateway.entity.OutboxEventEntity;
import com.marianna.gateway.port.OutboxRepository;
import com.marianna.gateway.repository.OutboxEventJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class OutboxRepositoryAdapter implements OutboxRepository {

    private final OutboxEventJpaRepository jpa;

    public OutboxRepositoryAdapter(OutboxEventJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(PaymentEvent event) {
        jpa.save(toEntity(event));
        log.debug("Saved outbox event: {} for payment id: {}", event.eventType(), event.aggregateId());
    }

    @Override
    public List<PaymentEvent> findUnpublishedEvents(int limit) {
        return jpa.findUnpublishedEvents(Pageable.ofSize(limit)).stream()
                .filter(e-> !e.isDeadLettered())
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<PaymentEvent> findByAggregateId(UUID aggregateId) {
        return jpa.findByAggregateId(aggregateId)
                .map(this::toDomain);
    }

    @Override
    @Transactional
    public void markAsPublished(UUID eventId) {
        jpa.markAsPublished(eventId);
        log.debug("Marked outbox event as published: {}", eventId);
    }

    @Override
    @Transactional
    public int recordFailure(UUID eventId, String error) {
        OutboxEventEntity event = jpa.findByIdForUpdate(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Outbox event not found: " + eventId));
        int attempts = event.getAttempts() + 1;
        event.setAttempts(attempts);
        event.setLastError(error);
        log.debug("Recorded failure for outbox event {} (attempt {})", eventId, attempts);
        return attempts;
    }

    @Override
    @Transactional
    public void markAsDeadLettered(UUID eventId) {
        jpa.markAsDeadLettered(eventId);
        log.debug("Marked outbox event as dead-lettered: {}", eventId);
    }

    private OutboxEventEntity toEntity(PaymentEvent event) {
        var e = new OutboxEventEntity();
        e.setId(event.eventId());
        e.setCustomerId(event.customerId());
        e.setEventType(event.eventType().name());
        e.setAggregateId(event.aggregateId());
        e.setPublished(false);
        e.setPayload(event.payload());
        return e;
    }

    private PaymentEvent toDomain(OutboxEventEntity e) {
        return new PaymentEvent(e.getId(), e.getAggregateId(), e.getCustomerId(),
                PaymentEventType.valueOf(e.getEventType()),
                e.getPayload(), e.getCreatedAt());
    }

}
