package com.marianna.gateway.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_status_created", columnList = "status, created_at") })
@Getter
@Setter
@NoArgsConstructor
public class OutboxEventEntity {

    private UUID id;
    private String aggregateType;
    private UUID aggregateId;
    private String eventType;
    @Column(columnDefinition = "jsonb")
    private String payload;
    private String status;
    private int retryCount;
    private Instant createdAt;
}
