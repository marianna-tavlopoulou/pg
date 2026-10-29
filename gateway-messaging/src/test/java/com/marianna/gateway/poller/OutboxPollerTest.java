package com.marianna.gateway.poller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marianna.gateway.domain.PaymentEvent;
import com.marianna.gateway.domain.PaymentEventType;
import com.marianna.gateway.port.EventPublisher;
import com.marianna.gateway.port.OutboxRepository;

@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private OutboxRepository outboxRepository;

    private OutboxPoller outboxPoller;

    @BeforeEach
    void setUp() {
        outboxPoller = new OutboxPoller(eventPublisher, outboxRepository);
    }

    @Test
    @DisplayName("Publishes each unpublished event and marks it as published")
    void shouldPublishAndMarkEachUnpublishedEvent() {
        PaymentEvent firstEvent = event();
        PaymentEvent secondEvent = event();
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(firstEvent, secondEvent));

        outboxPoller.pollOutbox();

        InOrder inOrder = inOrder(outboxRepository, eventPublisher);
        inOrder.verify(outboxRepository).findUnpublishedEvents(30);
        inOrder.verify(eventPublisher).publish(firstEvent);
        inOrder.verify(outboxRepository).markAsPublished(firstEvent.eventId());
        inOrder.verify(eventPublisher).publish(secondEvent);
        inOrder.verify(outboxRepository).markAsPublished(secondEvent.eventId());
    }

    @Test
    @DisplayName("Does nothing when there are no unpublished events")
    void shouldDoNothingWhenNoEventsAreUnpublished() {
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of());

        outboxPoller.pollOutbox();

        verify(outboxRepository).findUnpublishedEvents(30);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Skips the failed customer’s later events but continues with other customers")
    void shouldContinueWhenPublishingAnEventFails() {
        PaymentEvent failedEventCustomerX = event();
        PaymentEvent nextEventCustomerX = eventForCustomerId(failedEventCustomerX.customerId());
        PaymentEvent nextEventCustomerY = event();

        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(failedEventCustomerX, nextEventCustomerY, nextEventCustomerX));
        doThrow(new RuntimeException("Publish failed")).when(eventPublisher).publish(failedEventCustomerX);

        outboxPoller.pollOutbox();

        verify(eventPublisher).publish(failedEventCustomerX);
        verify(eventPublisher, never()).publish(nextEventCustomerX);
        verify(eventPublisher).publish(nextEventCustomerY);
        verify(outboxRepository, never()).markAsPublished(failedEventCustomerX.eventId());
        verify(outboxRepository).markAsPublished(nextEventCustomerY.eventId());
    }

    @Test
    @DisplayName("Skips a customer's later events when marking an event as published fails")
    void shouldSkipCustomerWhenMarkAsPublishedFails() {
        PaymentEvent firstEvent = event();
        PaymentEvent nextEvent = eventForCustomerId(firstEvent.customerId());
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(firstEvent, nextEvent));
        doThrow(new RuntimeException("Mark failed")).when(outboxRepository).markAsPublished(firstEvent.eventId());

        outboxPoller.pollOutbox();

        verify(eventPublisher).publish(firstEvent);
        verify(eventPublisher, never()).publish(nextEvent);
        verify(outboxRepository).markAsPublished(firstEvent.eventId());
        verify(outboxRepository, never()).markAsPublished(nextEvent.eventId());
    }

    @Test
    @DisplayName("A null customerId does not abort the batch")
    void shouldContinueBatchWhenCustomerIdIsNull() {
        PaymentEvent eventWithoutCustomerId = new PaymentEvent(UUID.randomUUID(), UUID.randomUUID(), null,
                PaymentEventType.PAYMENT_COMPLETED, "{}", Instant.now());
        PaymentEvent nextEvent = event();
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(eventWithoutCustomerId, nextEvent));
        doThrow(new NullPointerException("customerId")).when(eventPublisher).publish(eventWithoutCustomerId);

        outboxPoller.pollOutbox();

        verify(eventPublisher).publish(eventWithoutCustomerId);
        verify(eventPublisher).publish(nextEvent);
        verify(outboxRepository, never()).markAsPublished(eventWithoutCustomerId.eventId());
        verify(outboxRepository).markAsPublished(nextEvent.eventId());
    }

    @Test
    @DisplayName("Stops the batch when publishing times out")
    void shouldStopBatchWhenPublishingTimesOut() {
        PaymentEvent timedOutEvent = event();
        PaymentEvent nextEvent = event();
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(timedOutEvent, nextEvent));
        doThrow(new RuntimeException("Timed out waiting for Kafka ack",
                new TimeoutException("Kafka acknowledgement timed out")))
                .when(eventPublisher).publish(timedOutEvent);

        outboxPoller.pollOutbox();

        verify(eventPublisher).publish(timedOutEvent);
        verify(eventPublisher, never()).publish(nextEvent);
        verify(outboxRepository, never()).markAsPublished(timedOutEvent.eventId());
        verify(outboxRepository, never()).markAsPublished(nextEvent.eventId());
    }

    @Test
    @DisplayName("Stops the batch when publishing throws Kafka TimeoutException")
    void shouldStopBatchWhenPublishingThrowsKafkaTimeoutException() {
        PaymentEvent timedOutEvent = event();
        PaymentEvent nextEvent = event();
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(timedOutEvent, nextEvent));
        doThrow(new org.apache.kafka.common.errors.TimeoutException("Kafka metadata timed out"))
                .when(eventPublisher).publish(timedOutEvent);

        outboxPoller.pollOutbox();

        verify(eventPublisher).publish(timedOutEvent);
        verify(eventPublisher, never()).publish(nextEvent);
        verify(outboxRepository, never()).markAsPublished(timedOutEvent.eventId());
        verify(outboxRepository, never()).markAsPublished(nextEvent.eventId());
    }

    @Test
    @DisplayName("Continues processing later events when marking an event as published fails")
    void shouldContinueWhenMarkingAnEventAsPublishedFails() {
        PaymentEvent firstEvent = event();
        PaymentEvent nextEvent = event();
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(firstEvent, nextEvent));
        doThrow(new RuntimeException("Mark failed")).when(outboxRepository).markAsPublished(firstEvent.eventId());

        outboxPoller.pollOutbox();

        verify(eventPublisher).publish(firstEvent);
        verify(eventPublisher).publish(nextEvent);
        verify(outboxRepository).markAsPublished(firstEvent.eventId());
        verify(outboxRepository).markAsPublished(nextEvent.eventId());
    }

    private PaymentEvent event() {
        return new PaymentEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                PaymentEventType.PAYMENT_COMPLETED, "{}", Instant.now());
    }

    private PaymentEvent eventForCustomerId(UUID customerId) {
        return new PaymentEvent(UUID.randomUUID(), UUID.randomUUID(), customerId,
                PaymentEventType.PAYMENT_COMPLETED, "{}", Instant.now());
    }

}
