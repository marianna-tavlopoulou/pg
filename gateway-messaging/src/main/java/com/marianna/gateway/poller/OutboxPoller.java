package com.marianna.gateway.poller;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.kafka.common.errors.TimeoutException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.marianna.gateway.domain.PaymentEvent;
import com.marianna.gateway.port.EventPublisher;
import com.marianna.gateway.port.OutboxRepository;

import lombok.extern.slf4j.Slf4j;

@Component 
@Slf4j 
@ConditionalOnProperty(name = "gateway.poller.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPoller {

    private final EventPublisher eventPublisher;
    private final OutboxRepository outboxRepository;

    public OutboxPoller(EventPublisher eventPublisher, OutboxRepository outboxRepository) {
        this.eventPublisher = eventPublisher;
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(fixedDelayString = "${gateway.poller.outbox.fixed-delay-ms}", initialDelayString = "${gateway.poller.outbox.initial-delay-ms}")
    //fixedDelay prevents overlap between successive executions of this scheduled task, but it does not prevent multiple application instances from polling the same outbox rows concurrently.
    public void pollOutbox() {
        List<PaymentEvent> unpublishedEvents = outboxRepository.findUnpublishedEvents(30);
        Set<UUID> failedCustomerIds = new HashSet<>();
        for (PaymentEvent event : unpublishedEvents) {
            try {
                if (failedCustomerIds.contains(event.customerId())) {
                    log.warn("Skipping event {} for aggregateId: {} for customerId: {} because a previous event for this customer failed to publish in this poll cycle", event.eventType(), event.aggregateId(), event.customerId());
                    continue;
                }
                eventPublisher.publish(event);
                try {
                    outboxRepository.markAsPublished(event.eventId());
                } catch (Exception e) {
                    log.error("Failed to mark event {} with eventId: {} as published for aggregateId: {} for customerId: {}", event.eventType(), event.eventId(), event.aggregateId(), event.customerId(), e);
                    failedCustomerIds.add(event.customerId());
                }
            } catch (Exception e) {
                if (e instanceof TimeoutException
                        || e.getCause() instanceof TimeoutException
                        || e.getCause() instanceof java.util.concurrent.TimeoutException) {
                    log.error("Timeout occurred while publishing event {} for aggregateId: {} for customerId: {}", event.eventType(), event.aggregateId(), event.customerId());
                    return; // Exit the loop to retry on the next poll cycle, preventing further timeouts in this cycle
                }
                // Log the error, mark the customer as failed, and continue with the next customer's event
                log.error("Failed to publish event {} for aggregateId: {} for customerId: {}", event.eventType(), event.aggregateId(), event.customerId(), e);
                failedCustomerIds.add(event.customerId());
            }
        }
    }

}
