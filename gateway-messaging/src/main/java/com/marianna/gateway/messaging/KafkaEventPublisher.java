package com.marianna.gateway.messaging;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marianna.gateway.domain.PaymentEvent;
import com.marianna.gateway.port.EventPublisher;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private static final String DEAD_LETTER_TOPIC = "payment-events-dlt";
    private static final String DEAD_LETTER_REASON_HEADER = "dlt-reason";

    private final Integer sendTimeoutMs;
    private final ObjectMapper objectMapper;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
            @Value("${gateway.messaging.payment-events-topic}") String topic,
            @Value("${gateway.messaging.send-timeout-ms}") Integer sendTimeoutMs, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.sendTimeoutMs = sendTimeoutMs;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(PaymentEvent event) {
        String payload = serialize(event);
        String customerId = event.customerId().toString();

        try {
            log.debug("Publishing event {} to topic {} for aggregateId: {} for customerId: {}", event.eventType(),
                    topic,
                    event.aggregateId(), customerId);
            // Block until Kafka acknowledges. This ensures markAsPublished()
            // in the OutboxPoller is only called after confirmed delivery.
            // If this throws, the poller catches per-row, logs, and retries
            // on the next poll cycle — at-least-once delivery guaranteed.
            awaitAcknowledgement(kafkaTemplate.send(topic, customerId, payload), topic, event);
        } catch (RuntimeException e) {
            log.error("Failed to publish event {} to topic {} for aggregateId: {} for customerId: {}",
                    event.eventType(), topic, event.aggregateId(), customerId, e);
            throw e;
        }
    }

    @Override
    public void publishToDeadLetter(PaymentEvent event, String reason) {
        String payload = serialize(event);
        String customerId = event.customerId().toString();
        ProducerRecord<String, String> record = new ProducerRecord<>(DEAD_LETTER_TOPIC, customerId, payload);
        record.headers().add(DEAD_LETTER_REASON_HEADER, reason.getBytes(StandardCharsets.UTF_8));

        try {
            awaitAcknowledgement(kafkaTemplate.send(record), DEAD_LETTER_TOPIC, event);
        } catch (RuntimeException e) {
            log.error("Failed to publish event {} to dead-letter topic for aggregateId: {} for customerId: {}",
                    event.eventType(), event.aggregateId(), customerId, e);
            throw e;
        }
    }

    private String serialize(PaymentEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize PaymentEvent with id {}", event.eventId(), e);
            throw new RuntimeException("Failed to serialize PaymentEvent", e);
        }
    }

    private void awaitAcknowledgement(CompletableFuture<SendResult<String, String>> future,
            String destinationTopic, PaymentEvent event) {
        try {
            SendResult<String, String> result = future.get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            var metadata = result.getRecordMetadata();
            log.info("Successfully published event {} to topic {} partition {} at offset {} for payment: {}",
                    event.eventType(), destinationTopic, metadata.partition(), metadata.offset(), event.aggregateId());
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to publish event " + event.eventId(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for Kafka ack, event=" + event.eventId(), e);
        } catch (TimeoutException e) {
            throw new RuntimeException("Timed out waiting for Kafka ack, event=" + event.eventId(), e);
        }
    }

}
