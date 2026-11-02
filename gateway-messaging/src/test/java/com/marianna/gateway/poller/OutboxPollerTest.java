package com.marianna.gateway.poller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
        outboxPoller = new OutboxPoller(eventPublisher, outboxRepository, 5, 3);
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
    @DisplayName("Records a publish failure below the maximum attempts without sending to DLT")
    void shouldRecordFailureBelowMaxAttemptsWithoutDeadLettering() {
        PaymentEvent failedEvent = event();
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(failedEvent));
        doThrow(new RuntimeException("Serialization failed")).when(eventPublisher).publish(failedEvent);
        when(outboxRepository.recordFailure(failedEvent.eventId(), "RuntimeException: Serialization failed")).thenReturn(2);

        outboxPoller.pollOutbox();

        verify(outboxRepository).recordFailure(failedEvent.eventId(), "RuntimeException: Serialization failed");
        verify(eventPublisher, never()).publishToDeadLetter(any(), any());
        verify(outboxRepository, never()).markAsDeadLettered(any());
    }

    @Test
    @DisplayName("Dead-letters an event at max attempts and continues with later events for that customer")
    void shouldDeadLetterAtMaxAttemptsAndContinueCustomerEvents() {
        PaymentEvent failedEvent = event();
        PaymentEvent nextEvent = eventForCustomerId(failedEvent.customerId());
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(failedEvent, nextEvent));
        doThrow(new RuntimeException("Serialization failed")).when(eventPublisher).publish(failedEvent);
        when(outboxRepository.recordFailure(failedEvent.eventId(), "RuntimeException: Serialization failed")).thenReturn(5);

        outboxPoller.pollOutbox();

        verify(outboxRepository).recordFailure(failedEvent.eventId(), "RuntimeException: Serialization failed");
        verify(eventPublisher).publishToDeadLetter(failedEvent, "RuntimeException: Serialization failed");
        verify(outboxRepository).markAsDeadLettered(failedEvent.eventId());
        verify(eventPublisher).publish(nextEvent);
        verify(outboxRepository).markAsPublished(nextEvent.eventId());
    }

    @Test
    @DisplayName("Uses the root cause message when the publisher wraps a send failure")
    void shouldUseRootCauseMessageForWrappedSendFailure() {
        PaymentEvent failedEvent = event();
        String rootCauseMessage = "The message is 2097152 bytes when serialized which is larger than 1048576";
        String expectedReason = "RecordTooLargeException: " + rootCauseMessage;
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(failedEvent));
        doThrow(new RuntimeException("Failed to publish event " + failedEvent.eventId(),
                new org.apache.kafka.common.errors.RecordTooLargeException(rootCauseMessage)))
                .when(eventPublisher).publish(failedEvent);
        when(outboxRepository.recordFailure(failedEvent.eventId(), expectedReason)).thenReturn(5);

        outboxPoller.pollOutbox();

        verify(outboxRepository).recordFailure(failedEvent.eventId(), expectedReason);
        verify(eventPublisher).publishToDeadLetter(failedEvent, expectedReason);
        verify(outboxRepository).markAsDeadLettered(failedEvent.eventId());
    }

    @Test
    @DisplayName("Truncates the failure reason to 500 characters")
    void shouldTruncateLongFailureReason() {
        PaymentEvent failedEvent = event();
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(failedEvent));
        doThrow(new RuntimeException("Failed to publish event " + failedEvent.eventId(),
                new org.apache.kafka.common.errors.RecordTooLargeException("x".repeat(2000))))
                .when(eventPublisher).publish(failedEvent);
        when(outboxRepository.recordFailure(any(), anyString())).thenReturn(1);

        outboxPoller.pollOutbox();

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(outboxRepository).recordFailure(org.mockito.ArgumentMatchers.eq(failedEvent.eventId()), reason.capture());
        assertEquals(500, reason.getValue().length());
        assertTrue(reason.getValue().startsWith("RecordTooLargeException: "));
    }

    @Test
    @DisplayName("Falls back to the root cause class name when it has no message")
    void shouldUseRootCauseClassNameWhenRootCauseHasNoMessage() {
        PaymentEvent failedEvent = event();
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(failedEvent));
        doThrow(new RuntimeException("Failed to serialize PaymentEvent",
                new IllegalStateException(new UnsupportedOperationException())))
                .when(eventPublisher).publish(failedEvent);
        when(outboxRepository.recordFailure(failedEvent.eventId(), "UnsupportedOperationException")).thenReturn(1);

        outboxPoller.pollOutbox();

        verify(outboxRepository).recordFailure(failedEvent.eventId(), "UnsupportedOperationException");
        verify(eventPublisher, never()).publishToDeadLetter(any(), any());
    }

    @Test
    @DisplayName("Retries dead-letter publishing next cycle without marking the event dead-lettered")
    void shouldRetryDeadLetterPublishOnNextPoll() {
        PaymentEvent failedEvent = event();
        PaymentEvent nextEvent = eventForCustomerId(failedEvent.customerId());
        when(outboxRepository.findUnpublishedEvents(30))
                .thenAnswer(invocation -> List.of(failedEvent, nextEvent));
        doThrow(new RuntimeException("Serialization failed")).when(eventPublisher).publish(failedEvent);
        when(outboxRepository.recordFailure(failedEvent.eventId(), "RuntimeException: Serialization failed")).thenReturn(5, 6);
        doThrow(new RuntimeException("DLT unavailable"))
                .doNothing()
                .when(eventPublisher).publishToDeadLetter(failedEvent, "RuntimeException: Serialization failed");

        outboxPoller.pollOutbox();

        verify(outboxRepository, never()).markAsDeadLettered(any());
        verify(eventPublisher, never()).publish(nextEvent);

        outboxPoller.pollOutbox();

        verify(eventPublisher, org.mockito.Mockito.times(2)).publish(failedEvent);
        verify(eventPublisher).publish(nextEvent);
        verify(outboxRepository).markAsPublished(nextEvent.eventId());
        verify(eventPublisher, org.mockito.Mockito.times(2))
                .publishToDeadLetter(failedEvent, "RuntimeException: Serialization failed");
        verify(outboxRepository).markAsDeadLettered(failedEvent.eventId());
    }

    @Test
    @DisplayName("Stops the batch when recording a publish failure fails")
    void shouldStopBatchWhenRecordingFailureFails() {
        PaymentEvent failedEvent = event();
        PaymentEvent nextEventOtherCustomer = event();
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(failedEvent, nextEventOtherCustomer));
        doThrow(new RuntimeException("Publish failed")).when(eventPublisher).publish(failedEvent);
        when(outboxRepository.recordFailure(failedEvent.eventId(), "RuntimeException: Publish failed"))
                .thenThrow(new RuntimeException("Database unavailable"));

        outboxPoller.pollOutbox();

        verify(eventPublisher, never()).publish(nextEventOtherCustomer);
        verify(eventPublisher, never()).publishToDeadLetter(any(), any());
        verify(outboxRepository, never()).markAsDeadLettered(any());
    }

    @Test
    @DisplayName("Stops the batch when marking an event as dead-lettered fails")
    void shouldStopBatchWhenMarkingAsDeadLetteredFails() {
        PaymentEvent failedEvent = event();
        PaymentEvent nextEventOtherCustomer = event();
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(failedEvent, nextEventOtherCustomer));
        doThrow(new RuntimeException("Publish failed")).when(eventPublisher).publish(failedEvent);
        when(outboxRepository.recordFailure(failedEvent.eventId(), "RuntimeException: Publish failed")).thenReturn(5);
        doThrow(new RuntimeException("Database unavailable"))
                .when(outboxRepository).markAsDeadLettered(failedEvent.eventId());

        outboxPoller.pollOutbox();

        verify(eventPublisher).publishToDeadLetter(failedEvent, "RuntimeException: Publish failed");
        verify(eventPublisher, never()).publish(nextEventOtherCustomer);
    }

    @Test
    @DisplayName("Stops the batch when dead-letter publishing times out")
    void shouldStopBatchWhenDeadLetterPublishTimesOut() {
        PaymentEvent failedEvent = event();
        PaymentEvent nextEventOtherCustomer = event();
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(failedEvent, nextEventOtherCustomer));
        doThrow(new RuntimeException("Publish failed")).when(eventPublisher).publish(failedEvent);
        when(outboxRepository.recordFailure(failedEvent.eventId(), "RuntimeException: Publish failed")).thenReturn(5);
        doThrow(new RuntimeException("Timed out waiting for Kafka ack",
                new TimeoutException("Kafka acknowledgement timed out")))
                .when(eventPublisher).publishToDeadLetter(failedEvent, "RuntimeException: Publish failed");

        outboxPoller.pollOutbox();

        verify(outboxRepository, never()).markAsDeadLettered(any());
        verify(eventPublisher, never()).publish(nextEventOtherCustomer);
    }

    @Test
    @DisplayName("Blocks only the customer when dead-letter publishing fails without a timeout")
    void shouldBlockOnlyCustomerWhenDeadLetterPublishFails() {
        PaymentEvent failedEvent = event();
        PaymentEvent nextEventSameCustomer = eventForCustomerId(failedEvent.customerId());
        PaymentEvent nextEventOtherCustomer = event();
        when(outboxRepository.findUnpublishedEvents(30))
                .thenReturn(List.of(failedEvent, nextEventOtherCustomer, nextEventSameCustomer));
        doThrow(new RuntimeException("Publish failed")).when(eventPublisher).publish(failedEvent);
        when(outboxRepository.recordFailure(failedEvent.eventId(), "RuntimeException: Publish failed")).thenReturn(5);
        doThrow(new RuntimeException("DLT unavailable"))
                .when(eventPublisher).publishToDeadLetter(failedEvent, "RuntimeException: Publish failed");

        outboxPoller.pollOutbox();

        verify(outboxRepository, never()).markAsDeadLettered(any());
        verify(eventPublisher, never()).publish(nextEventSameCustomer);
        verify(eventPublisher).publish(nextEventOtherCustomer);
        verify(outboxRepository).markAsPublished(nextEventOtherCustomer.eventId());
    }

    @Test
    @DisplayName("Does not hang when the exception cause chain contains a cycle")
    void shouldNotHangOnCyclicCauseChain() {
        PaymentEvent failedEvent = event();
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b");
        a.initCause(b);
        b.initCause(a);
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(failedEvent));
        doThrow(a).when(eventPublisher).publish(failedEvent);
        when(outboxRepository.recordFailure(any(), anyString())).thenReturn(1);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> outboxPoller.pollOutbox());

        verify(outboxRepository).recordFailure(any(), anyString());
    }

    @Test
    @DisplayName("Stops the batch when a TimeoutException is deep in the cause chain")
    void shouldDetectTimeoutDeepInCauseChain() {
        PaymentEvent timedOutEvent = event();
        PaymentEvent nextEvent = event();
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(timedOutEvent, nextEvent));
        doThrow(new RuntimeException("level 1",
                new RuntimeException("level 2",
                        new RuntimeException("level 3",
                                new TimeoutException("Kafka acknowledgement timed out")))))
                .when(eventPublisher).publish(timedOutEvent);

        outboxPoller.pollOutbox();

        verify(outboxRepository, never()).recordFailure(any(), any());
        verify(eventPublisher, never()).publish(nextEvent);
    }

    @Test
    @DisplayName("Stops the batch without recording a failure when publishing is interrupted")
    void shouldStopBatchWithoutRecordingFailureWhenInterrupted() {
        PaymentEvent interruptedEvent = event();
        PaymentEvent nextEventOtherCustomer = event();
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(interruptedEvent, nextEventOtherCustomer));
        doThrow(new RuntimeException("Interrupted waiting for Kafka ack", new InterruptedException()))
                .when(eventPublisher).publish(interruptedEvent);

        outboxPoller.pollOutbox();

        verify(outboxRepository, never()).recordFailure(any(), any());
        verify(eventPublisher, never()).publish(nextEventOtherCustomer);
    }

    @Test
    @DisplayName("Stops the batch when dead-letter publishing is interrupted")
    void shouldStopBatchWhenDeadLetterPublishIsInterrupted() {
        PaymentEvent failedEvent = event();
        PaymentEvent nextEventOtherCustomer = event();
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(failedEvent, nextEventOtherCustomer));
        doThrow(new RuntimeException("Publish failed")).when(eventPublisher).publish(failedEvent);
        when(outboxRepository.recordFailure(failedEvent.eventId(), "RuntimeException: Publish failed")).thenReturn(5);
        doThrow(new RuntimeException("Interrupted waiting for Kafka ack", new InterruptedException()))
                .when(eventPublisher).publishToDeadLetter(failedEvent, "RuntimeException: Publish failed");

        outboxPoller.pollOutbox();

        verify(outboxRepository, never()).markAsDeadLettered(any());
        verify(eventPublisher, never()).publish(nextEventOtherCustomer);
    }

    @Test
    @DisplayName("Blocks the customer without parking when below the park threshold")
    void shouldBlockCustomerWithoutParkingBelowParkThreshold() {
        PaymentEvent failedEvent = event();
        PaymentEvent nextEventSameCustomer = eventForCustomerId(failedEvent.customerId());
        PaymentEvent nextEventOtherCustomer = event();
        when(outboxRepository.findUnpublishedEvents(30))
                .thenReturn(List.of(failedEvent, nextEventOtherCustomer, nextEventSameCustomer));
        doThrow(new RuntimeException("Publish failed")).when(eventPublisher).publish(failedEvent);
        when(outboxRepository.recordFailure(failedEvent.eventId(), "RuntimeException: Publish failed")).thenReturn(6);
        doThrow(new RuntimeException("DLT unavailable"))
                .when(eventPublisher).publishToDeadLetter(failedEvent, "RuntimeException: Publish failed");

        outboxPoller.pollOutbox();

        verify(outboxRepository, never()).markAsParked(any());
        verify(eventPublisher, never()).publish(nextEventSameCustomer);
        verify(eventPublisher).publish(nextEventOtherCustomer);
    }

    @Test
    @DisplayName("Parks the event and unblocks the customer at the park threshold")
    void shouldParkAndUnblockCustomerAtParkThreshold() {
        PaymentEvent failedEvent = event();
        PaymentEvent nextEventSameCustomer = eventForCustomerId(failedEvent.customerId());
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(failedEvent, nextEventSameCustomer));
        doThrow(new RuntimeException("Publish failed")).when(eventPublisher).publish(failedEvent);
        when(outboxRepository.recordFailure(failedEvent.eventId(), "RuntimeException: Publish failed")).thenReturn(8);
        doThrow(new RuntimeException("DLT unavailable"))
                .when(eventPublisher).publishToDeadLetter(failedEvent, "RuntimeException: Publish failed");

        outboxPoller.pollOutbox();

        verify(outboxRepository).markAsParked(failedEvent.eventId());
        verify(outboxRepository, never()).markAsDeadLettered(any());
        verify(eventPublisher).publish(nextEventSameCustomer);
        verify(outboxRepository).markAsPublished(nextEventSameCustomer.eventId());
    }

    @Test
    @DisplayName("Stops the batch when parking an event fails")
    void shouldStopBatchWhenParkingFails() {
        PaymentEvent failedEvent = event();
        PaymentEvent nextEventOtherCustomer = event();
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(failedEvent, nextEventOtherCustomer));
        doThrow(new RuntimeException("Publish failed")).when(eventPublisher).publish(failedEvent);
        when(outboxRepository.recordFailure(failedEvent.eventId(), "RuntimeException: Publish failed")).thenReturn(8);
        doThrow(new RuntimeException("DLT unavailable"))
                .when(eventPublisher).publishToDeadLetter(failedEvent, "RuntimeException: Publish failed");
        doThrow(new RuntimeException("Database unavailable"))
                .when(outboxRepository).markAsParked(failedEvent.eventId());

        outboxPoller.pollOutbox();

        verify(eventPublisher, never()).publish(nextEventOtherCustomer);
    }

    @Test
    @DisplayName("Does not park when dead-letter publishing times out at the park threshold")
    void shouldNotParkWhenDeadLetterPublishTimesOutAtThreshold() {
        PaymentEvent failedEvent = event();
        PaymentEvent nextEventOtherCustomer = event();
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(failedEvent, nextEventOtherCustomer));
        doThrow(new RuntimeException("Publish failed")).when(eventPublisher).publish(failedEvent);
        when(outboxRepository.recordFailure(failedEvent.eventId(), "RuntimeException: Publish failed")).thenReturn(8);
        doThrow(new RuntimeException("Timed out waiting for Kafka ack",
                new TimeoutException("Kafka acknowledgement timed out")))
                .when(eventPublisher).publishToDeadLetter(failedEvent, "RuntimeException: Publish failed");

        outboxPoller.pollOutbox();

        verify(outboxRepository, never()).markAsParked(any());
        verify(eventPublisher, never()).publish(nextEventOtherCustomer);
    }

    @Test
    @DisplayName("Does not record a failure when publishing times out")
    void shouldNotRecordFailureWhenPublishingTimesOut() {
        PaymentEvent timedOutEvent = event();
        when(outboxRepository.findUnpublishedEvents(30)).thenReturn(List.of(timedOutEvent));
        doThrow(new RuntimeException("Timed out waiting for Kafka ack",
                new TimeoutException("Kafka acknowledgement timed out")))
                .when(eventPublisher).publish(timedOutEvent);

        outboxPoller.pollOutbox();

        verify(outboxRepository, never()).recordFailure(any(), any());
        verify(eventPublisher, never()).publishToDeadLetter(any(), any());
        verify(outboxRepository, never()).markAsDeadLettered(any());
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
    @DisplayName("Stops the batch when marking an event as published fails")
    void shouldStopBatchWhenMarkingAnEventAsPublishedFails() {
        PaymentEvent firstEvent = event();
        PaymentEvent nextEventOtherCustomer = event();
        PaymentEvent nextEventSameCustomer = eventForCustomerId(firstEvent.customerId());
        when(outboxRepository.findUnpublishedEvents(30))
                .thenReturn(List.of(firstEvent, nextEventOtherCustomer, nextEventSameCustomer));
        doThrow(new RuntimeException("Mark failed")).when(outboxRepository).markAsPublished(firstEvent.eventId());

        outboxPoller.pollOutbox();

        verify(eventPublisher).publish(firstEvent);
        verify(eventPublisher, never()).publish(nextEventOtherCustomer);
        verify(eventPublisher, never()).publish(nextEventSameCustomer);
        verify(outboxRepository).markAsPublished(firstEvent.eventId());
        verify(outboxRepository, never()).markAsPublished(nextEventOtherCustomer.eventId());
        verify(outboxRepository, never()).markAsPublished(nextEventSameCustomer.eventId());
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
