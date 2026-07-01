package com.marianna.gateway.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Core domain object — zero JPA/Spring annotations.
 * Interview talking point: hexagonal architecture keeps domain pure and
 * testable.
 */
public record PaymentOrder(
        UUID id,
        UUID customerId,
        UUID merchantId,
        BigDecimal amount,
        Currency currency,
        PaymentMethod method,
        PaymentStatus status,
        String idempotencyKey,
        String description,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    /*
     * Builder for brand-new payments — version is null because the row does
     * not exist in the database yet. Hibernate will assign version = 0 on INSERT.
     */
    public static PaymentOrder create(UUID customerId, UUID merchantId, BigDecimal amount,
            Currency currency, PaymentMethod method,
            String idempotencyKey, String description, Long version) {
        return new PaymentOrder(UUID.randomUUID(), customerId, merchantId, amount, currency,
                method, PaymentStatus.PENDING, idempotencyKey, description,
                Instant.now(), Instant.now(), version);
    }

    /**
     * Returns a copy with a new status, carrying all other fields including
     * version. The version must survive status transitions so the adapter's
     * subsequent save() can still prove to Hibernate that the caller is working
     * from the same row generation it originally loaded.
     */
    public PaymentOrder withStatus(PaymentStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    "Cannot transition %s from %s to %s".formatted(id, status, newStatus));
        }
        return new PaymentOrder(id, customerId, merchantId, amount, currency, method,
                newStatus, idempotencyKey, description, createdAt, Instant.now(), version);
    }
}
