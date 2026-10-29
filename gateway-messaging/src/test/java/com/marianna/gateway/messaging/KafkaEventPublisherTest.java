package com.marianna.gateway.messaging;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.marianna.gateway.domain.Currency;
import com.marianna.gateway.domain.PaymentEvent;
import com.marianna.gateway.domain.PaymentEventType;
import com.marianna.gateway.domain.PaymentMethod;
import com.marianna.gateway.domain.PaymentOrder;
import com.marianna.gateway.domain.PaymentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

    private static final String TOPIC = "payment-events";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private SendResult<String, String> sendResult;

    @Mock
    private RecordMetadata recordMetadata;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, String>> producerRecordCaptor;

    private KafkaEventPublisher kafkaEventPublisher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        kafkaEventPublisher = new KafkaEventPublisher(kafkaTemplate, TOPIC, 5000, objectMapper);
    }

    @Test
    @DisplayName("Should publish event to Kafka with customerId as the routing partition key")
    void shouldPublishWithCustomerIdAsPartitionKey() throws Exception {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        when(sendResult.getRecordMetadata()).thenReturn(recordMetadata);

        UUID aggregateId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        PaymentOrder payment = new PaymentOrder(aggregateId, customerId, UUID.randomUUID(), new BigDecimal("1000"),
                Currency.EUR, PaymentMethod.BANK_TRANSFER, PaymentStatus.COMPLETED, UUID.randomUUID().toString(), "pay",
                Instant.now(), Instant.now(), null);
        String jsonPayment = objectMapper.writeValueAsString(payment);
        PaymentEvent event = new PaymentEvent(eventId, aggregateId, customerId, PaymentEventType.PAYMENT_COMPLETED,
                jsonPayment,
                Instant.now());
        String jsonEvent = objectMapper.writeValueAsString(event);

        kafkaEventPublisher.publish(event);

        verify(kafkaTemplate).send(eq(TOPIC), eq(customerId.toString()), eq(jsonEvent));
    }

    @Test
    @DisplayName("Should fail when waiting for Kafka acknowledgement times out")
    void shouldThrowWhenKafkaAcknowledgementTimesOut() throws Exception {
        PaymentEvent event = event();
        TimeoutException timeout = new TimeoutException("Kafka acknowledgement timed out");
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> sendFuture = mock(CompletableFuture.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(sendFuture);
        when(sendFuture.get(5000, TimeUnit.MILLISECONDS)).thenThrow(timeout);

        assertThatThrownBy(() -> kafkaEventPublisher.publish(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Timed out waiting for Kafka ack")
                .hasCause(timeout);
    }

    @Test
    @DisplayName("Should preserve the cause when Kafka returns a failed future")
    void shouldPreserveCauseWhenKafkaFutureFails() {
        PaymentEvent event = event();
        RuntimeException failure = new RuntimeException("Kafka send failed");
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(failure));

        assertThatThrownBy(() -> kafkaEventPublisher.publish(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to publish event")
                .hasCause(failure);
    }

    @Test
    @DisplayName("Should publish failed event to DLT with the failure reason in a header")
    void shouldPublishToDeadLetterTopicWithReasonHeader() throws Exception {
        PaymentEvent event = event();
        String reason = "Kafka unavailable after retry limit";
        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, String>>any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        when(sendResult.getRecordMetadata()).thenReturn(recordMetadata);

        kafkaEventPublisher.publishToDeadLetter(event, reason);

        verify(kafkaTemplate).send(producerRecordCaptor.capture());
        ProducerRecord<String, String> record = producerRecordCaptor.getValue();
        assertThat(record.topic()).isEqualTo("payment-events-dlt");
        assertThat(record.key()).isEqualTo(event.customerId().toString());
        assertThat(record.value()).isEqualTo(objectMapper.writeValueAsString(event));
        assertThat(record.headers().lastHeader("reason").value())
                .isEqualTo(reason.getBytes(StandardCharsets.UTF_8));
    }

    private PaymentEvent event() {
        return new PaymentEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                PaymentEventType.PAYMENT_COMPLETED, "{}", Instant.now());
    }

}