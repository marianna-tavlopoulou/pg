package com.marianna.gateway.adapter;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

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
                .map(this::toDomain)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public void markAsPublished(java.util.UUID eventId) {
        jpa.findById(eventId).ifPresent(e -> {
            e.setPublished(true);
            jpa.save(e);
            log.debug("Marked outbox event as published: {} for payment id: {}", e.getEventType(), e.getAggregateId());
        });
    }

    private OutboxEventEntity toEntity(PaymentEvent event) {
        var e = new OutboxEventEntity();
        e.setId(event.eventId());
        e.setEventType(event.eventType().name());
        e.setAggregateId(event.aggregateId());
        e.setPublished(false);
        e.setPayload(event.payload());
        e.setCreatedAt(Instant.now());
        return e;
    }

    private PaymentEvent toDomain(OutboxEventEntity e) {
        return new PaymentEvent(e.getId(), e.getAggregateId(), PaymentEventType.valueOf(e.getEventType()),
                e.getPayload(), e.getCreatedAt());
    }

}
