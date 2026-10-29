package com.marianna.gateway.domain;

import java.time.Instant;
import java.util.UUID;

public record PaymentEvent(UUID eventId, UUID aggregateId, UUID customerId, PaymentEventType eventType, String payload,
                Instant occurredAt) {

}
