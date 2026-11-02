package com.marianna.gateway.poller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
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

    private static final int MAX_CAUSE_DEPTH = 20;
    private static final int MAX_REASON_LENGTH = 500;

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
                if (isInterrupt(e)) {
                    log.warn("Interrupted while publishing event {}; stopping poll cycle", event.eventId());
                    return; // Shutting down: do not count an attempt, and later sends would fail immediately
                }
                if (isTimeout(e)) {
                    log.error("Timeout occurred while publishing event {} for aggregateId: {} for customerId: {}", event.eventType(), event.aggregateId(), event.customerId());
                    return; // Exit the loop to retry on the next poll cycle, preventing further timeouts in this cycle
                }

                String reason = failureReason(e);
                int attempts;
                try {
                    attempts = outboxRepository.recordFailure(event.eventId(), reason);
                } catch (Exception recordFailureException) {
                    log.error("Failed to record publish failure for outbox event {}", event.eventId(),
                            recordFailureException);
                    return; // The database is likely unavailable, so stop the batch and retry on the next poll cycle
                }

                if (attempts < maxAttempts) {
                    failedCustomerIds.add(event.customerId());
                    continue;
                }

                try {
                    eventPublisher.publishToDeadLetter(event, reason);
                } catch (Exception deadLetterException) {
                    if (isInterrupt(deadLetterException)) {
                        log.warn("Interrupted while dead-lettering event {}; stopping poll cycle", event.eventId());
                        return; // Shutting down: retry the dead-letter publish on the next start
                    }
                    if (isTimeout(deadLetterException)) {
                        log.error("Timeout occurred while dead-lettering event {} for aggregateId: {} for customerId: {}",
                                event.eventId(), event.aggregateId(), event.customerId());
                        return; // Same as the main path: stop the batch to avoid further timeouts in this cycle
                    }
                    log.error("Failed to publish outbox event {} to dead-letter topic", event.eventId(),
                            deadLetterException);
                    failedCustomerIds.add(event.customerId());
                    continue;
                }

                try {
                    outboxRepository.markAsDeadLettered(event.eventId());
                } catch (Exception markDeadLetteredException) {
                    log.error("Failed to mark outbox event {} as dead-lettered", event.eventId(),
                            markDeadLetteredException);
                    return; // The database is likely unavailable, so stop the batch and retry on the next poll cycle
                }
                log.warn("Dead-lettered event {} after {} attempts", event.eventId(), attempts);
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
        for (Throwable current : causeChain(error)) {
            if (current instanceof TimeoutException || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
        }
        return false;
    }

    private boolean isInterrupt(Throwable error) {
        for (Throwable current : causeChain(error)) {
            if (current instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }

    // The publisher wraps failures in generic RuntimeExceptions, so the root cause carries the useful detail.
    // The reason is stored in last_error and the dlt-reason header, so it is truncated: the message may contain
    // payment data, and a long header makes the DLT record bigger. The full exception is in the publisher's logs.
    private String failureReason(Exception error) {
        List<Throwable> chain = causeChain(error);
        Throwable rootCause = chain.get(chain.size() - 1);
        String name = rootCause.getClass().getSimpleName();
        if (name.isEmpty()) {
            name = rootCause.getClass().getName();
        }
        String message = rootCause.getMessage();
        String reason = message == null || message.isBlank() ? name : name + ": " + message;
        return reason.length() > MAX_REASON_LENGTH ? reason.substring(0, MAX_REASON_LENGTH) : reason;
    }

    // Bounded and cycle-safe, so a malformed cause chain cannot block the scheduler thread.
    private List<Throwable> causeChain(Throwable error) {
        List<Throwable> chain = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = error;
        while (current != null && chain.size() < MAX_CAUSE_DEPTH && seen.add(current)) {
            chain.add(current);
            current = current.getCause();
        }
        return chain;
    }

}
