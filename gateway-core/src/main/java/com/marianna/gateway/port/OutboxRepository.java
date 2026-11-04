package com.marianna.gateway.port;

import com.marianna.gateway.domain.PaymentEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxRepository {

    void save(PaymentEvent event);

    List<PaymentEvent> findUnpublishedEvents(int limit);

    void markAsPublished(UUID eventId);

    int recordFailure(UUID eventId, String error);

    void markAsDeadLettered(UUID eventId);

    void markAsParked(UUID eventId);

    Optional<PaymentEvent> findByAggregateId(UUID aggregateId);
}
