package com.marianna.gateway.poller;

import java.util.List;

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
        for (PaymentEvent event : unpublishedEvents) {
            try {
                eventPublisher.publish(event);
                try {
                    outboxRepository.markAsPublished(event.eventId());
                } catch (Exception e) {
                    log.error("Failed to mark event {} as published for aggregateId: {} for customerId: {}", event.eventType(), event.aggregateId(), event.customerId(), e);
                }
            } catch (Exception e) {
                if (e instanceof TimeoutException
                        || e.getCause() instanceof TimeoutException
                        || e.getCause() instanceof java.util.concurrent.TimeoutException) {
                    log.error("Timeout occurred while publishing event {} for aggregateId: {} for customerId: {}", event.eventType(), event.aggregateId(), event.customerId());
                    return; // Exit the loop to retry on the next poll cycle, preventing further timeouts in this cycle
                }
                // Log the error and continue with the next event
                log.error("Failed to publish event {} for aggregateId: {} for customerId: {}", event.eventType(), event.aggregateId(), event.customerId(), e);
            }
        }
    }

}
