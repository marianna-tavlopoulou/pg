package com.marianna.gateway.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_unpublished", columnList = "published, created_at") })
@Getter
@Setter
@NoArgsConstructor
public class OutboxEventEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id; // = PaymentEvent.eventId()

    // the payment_order id
    @Column(name = "aggregate_id", nullable = false, columnDefinition = "uuid")
    private UUID aggregateId; // = PaymentOrder.id()

    @Column(name = "event_type", nullable = false)
    private String eventType; // = PaymentEventType.name()

    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload; // Snapshot of PaymentOrder

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published", nullable = false)
    private Boolean published;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        published = false;
    }
}
