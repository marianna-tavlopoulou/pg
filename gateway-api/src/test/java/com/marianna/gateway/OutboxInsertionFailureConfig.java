package com.marianna.gateway;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.marianna.gateway.domain.PaymentEvent;
import com.marianna.gateway.port.OutboxRepository;

@TestConfiguration
class OutboxInsertionFailureConfig {

    @Bean
    @Primary
    OutboxRepository outboxRepository() {
        return new OutboxRepository() {
            @Override
            public void save(PaymentEvent event) {
                throw new RuntimeException("Simulate Exception when persisting in outbox_events.");
            }

            @Override
            public java.util.List<PaymentEvent> findUnpublishedEvents(int limit) {
                return java.util.Collections.emptyList();
            }

            @Override
            public void markAsPublished(java.util.UUID eventId) {
                // No-op
            }

            @Override
            public java.util.Optional<PaymentEvent> findByAggregateId(java.util.UUID aggregateId) {
                return java.util.Optional.empty();
            }
        };
    }

}
