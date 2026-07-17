package com.marianna.gateway.messaging;

import java.util.concurrent.ExecutionException;

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
    private final ObjectMapper objectMapper;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
            @Value("${gateway.messaging.payment-events-topic}") String topic, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(PaymentEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize PaymentEvent with id {}", event.eventId(), e);
            throw new RuntimeException("Failed to serialize PaymentEvent", e);
        }

        try {
            log.debug("Publishing event {} to topic {} for aggregateId: {} for customerId: {}", event.eventType(),
                    topic,
                    event.aggregateId(), event.customerId().toString());
            // Block until Kafka acknowledges. This ensures markAsPublished()
            // in the OutboxPoller is only called after confirmed delivery.
            // If this throws, the poller catches per-row, logs, and retries
            // on the next poll cycle — at-least-once delivery guaranteed.
            SendResult<String, String> result = kafkaTemplate.send(topic, event.customerId().toString(), payload).get();
            var metadata = result.getRecordMetadata();
            log.info("Successfully published event {} to partition {} at offset {} for payment: {}",
                    event.eventType(),
                    metadata.partition(), metadata.offset(), event.aggregateId());
        } catch (ExecutionException e) {
            log.error("Failed to publish event {} to topic {} for aggregateId: {} for customerId: {}",
                    event.eventType(),
                    topic, event.aggregateId(), event.customerId().toString(), e.getCause());
            throw new RuntimeException("Failed to publish event " + event.eventId(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to publish event {} to topic {} for aggregateId: {} for customerId: {}",
                    event.eventType(),
                    topic, event.aggregateId(), event.customerId().toString(), e);
            throw new RuntimeException("Interrupted waiting for Kafka ack, event=" + event.eventId(), e);
        }

    }

}
