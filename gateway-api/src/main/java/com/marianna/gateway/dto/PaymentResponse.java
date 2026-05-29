package com.marianna.gateway.dto;

import com.marianna.gateway.domain.Currency;
import com.marianna.gateway.domain.PaymentMethod;
import com.marianna.gateway.domain.PaymentOrder;
import com.marianna.gateway.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
    UUID id, BigDecimal amount, Currency currency,
    PaymentMethod method, PaymentStatus status,
    String description, Instant createdAt, Instant updatedAt
) {
    public static PaymentResponse from(PaymentOrder o) {
        return new PaymentResponse(o.id(), o.amount(), o.currency(), o.method(),
            o.status(), o.description(), o.createdAt(), o.updatedAt());
    }
}
