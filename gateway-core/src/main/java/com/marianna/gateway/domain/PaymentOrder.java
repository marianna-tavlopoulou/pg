package com.marianna.gateway.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Core domain object — zero JPA/Spring annotations.
 * Interview talking point: hexagonal architecture keeps domain pure and testable.
 */
public record PaymentOrder(
    UUID id,
    UUID merchantId,
    BigDecimal amount,
    Currency currency,
    PaymentMethod method,
    PaymentStatus status,
    String idempotencyKey,
    String description,
    Instant createdAt,
    Instant updatedAt
) {
    public static PaymentOrder create(UUID merchantId, BigDecimal amount,
            Currency currency, PaymentMethod method,
            String idempotencyKey, String description) {
        return new PaymentOrder(UUID.randomUUID(), merchantId, amount, currency,
                method, PaymentStatus.PENDING, idempotencyKey, description,
                Instant.now(), Instant.now());
    }

    public PaymentOrder withStatus(PaymentStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                "Cannot transition %s from %s to %s".formatted(id, status, newStatus));
        }
        return new PaymentOrder(id, merchantId, amount, currency, method,
                newStatus, idempotencyKey, description, createdAt, Instant.now());
    }
}
