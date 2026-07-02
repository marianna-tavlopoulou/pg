package com.marianna.gateway.port;

import java.util.List;
import java.util.UUID;

import com.marianna.gateway.domain.PaymentEvent;

public interface OutboxRepository {

    void save(PaymentEvent event);

    List<PaymentEvent> findUnpublishedEvents(int limit);

    void markAsPublished(UUID eventId);

}
