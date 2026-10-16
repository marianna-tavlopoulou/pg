package com.marianna.gateway.poller;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.marianna.gateway.domain.PaymentEvent;
import com.marianna.gateway.port.EventPublisher;
import com.marianna.gateway.port.OutboxRepository;

import lombok.extern.slf4j.Slf4j;

@Component 
@Slf4j 
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
                outboxRepository.markAsPublished(event.eventId());
            } catch (Exception e) {
                // Log the error and continue with the next event
                log.error("Failed to publish event {} for aggregateId: {} for customerId: {}", event.eventType(), event.aggregateId(), event.customerId().toString(), e);
            }
        }
    }

}
