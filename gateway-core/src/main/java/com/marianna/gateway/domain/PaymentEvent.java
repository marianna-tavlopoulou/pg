package com.marianna.gateway.domain;

import java.time.Instant;
import java.util.UUID;

public record PaymentEvent(UUID eventId, UUID aggregateId, PaymentEventType eventType, String payload,
        Instant occurredAt) {

}
