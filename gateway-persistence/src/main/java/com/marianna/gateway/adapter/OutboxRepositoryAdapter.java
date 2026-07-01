package com.marianna.gateway.adapter;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.marianna.gateway.domain.PaymentOrder;
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

    public PaymentOrder save(PaymentOrder order) {
        var entity = createNewEntity(order);
        jpa.save(entity);
        return order;
    }

    private OutboxEventEntity createNewEntity(PaymentOrder o) {
        log.debug("Saving outbox entity with id: {}", o.id());
        var e = new OutboxEventEntity();
        e.setAggregateId(o.id());
        e.setAggregateType("PAYMENT");
        e.setEventType("PAYMENT_CREATED");
        e.setStatus("PENDING");
        e.setPayload(null);
        e.setRetryCount(0);
        e.setCreatedAt(Instant.now());

        return e;
    }

}
