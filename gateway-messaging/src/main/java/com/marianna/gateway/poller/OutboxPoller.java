package com.marianna.gateway.poller;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.kafka.common.errors.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
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
    private final int maxAttempts;

    public OutboxPoller(EventPublisher eventPublisher, OutboxRepository outboxRepository,
            @Value("${gateway.poller.outbox.max-attempts:5}") int maxAttempts) {
        this.eventPublisher = eventPublisher;
        this.outboxRepository = outboxRepository;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${gateway.poller.outbox.fixed-delay-ms}", initialDelayString = "${gateway.poller.outbox.initial-delay-ms}")
    //fixedDelay prevents overlap between successive executions of this scheduled task, but it does not prevent multiple application instances from polling the same outbox rows concurrently.
    public void pollOutbox() {
        List<PaymentEvent> unpublishedEvents = outboxRepository.findUnpublishedEvents(30);
        Set<UUID> failedCustomerIds = new HashSet<>();
        for (PaymentEvent event : unpublishedEvents) {
            if (failedCustomerIds.contains(event.customerId())) {
                log.warn("Skipping event {} for aggregateId: {} for customerId: {} because a previous event for this customer failed to publish in this poll cycle",
                        event.eventType(), event.aggregateId(), event.customerId());
                continue;
            }

            try {
                eventPublisher.publish(event);
            } catch (Exception e) {
                if (isTimeout(e)) {
                    log.error("Timeout occurred while publishing event {} for aggregateId: {} for customerId: {}", event.eventType(), event.aggregateId(), event.customerId());
                    return; // Exit the loop to retry on the next poll cycle, preventing further timeouts in this cycle
                }

                log.error("Failed to publish event {} for aggregateId: {} for customerId: {}", event.eventType(), event.aggregateId(), event.customerId(), e);
                String reason = failureReason(e);
                try {
                    int attempts = outboxRepository.recordFailure(event.eventId(), reason);
                    if (attempts >= maxAttempts) {
                        eventPublisher.publishToDeadLetter(event, reason);
                        outboxRepository.markAsDeadLettered(event.eventId());
                        log.warn("Dead-lettered event {} after {} attempts", event.eventId(), attempts);
                    } else {
                        failedCustomerIds.add(event.customerId());
                    }
                } catch (Exception failureHandlingException) {
                    log.error("Failed to record or dead-letter outbox event {}", event.eventId(),
                            failureHandlingException);
                    failedCustomerIds.add(event.customerId());
                }
                continue;
            }

            try {
                outboxRepository.markAsPublished(event.eventId());
            } catch (Exception e) {
                log.error("Failed to mark event {} with eventId: {} as published for aggregateId: {} for customerId: {}",
                        event.eventType(), event.eventId(), event.aggregateId(), event.customerId(), e);
                return;
            }
        }
    }

    private boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof TimeoutException || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String failureReason(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

}
