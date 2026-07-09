package com.marianna.gateway.messaging;

import java.util.concurrent.CompletableFuture;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marianna.gateway.domain.PaymentEvent;
import com.marianna.gateway.port.EventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String TOPIC = "payment-events";
    private final ObjectMapper objectMapper;

    @Override
    public void publish(PaymentEvent event, String customerId) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize PaymentEvent with id {}", event.eventId(), e);
            throw new RuntimeException("Failed to serialize PaymentEvent", e);
        }
        log.debug("Publishing event {} to topic {} for aggregateId: {} for customerId: {}", event.eventType(), TOPIC,
                event.aggregateId(), customerId);

        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(
                TOPIC, customerId, payload); // customerId: partition key to ensure ordering for the same customer

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event {} to topic {} for aggregateId: {} for customerId: {}",
                        event.eventType(),
                        TOPIC, event.aggregateId(), customerId, ex);
            } else {
                var metadata = result.getRecordMetadata();
                log.info("Successfully published event {} to partition {} at offset {} for payment: {}",
                        event.eventType(),
                        metadata.partition(), metadata.offset(), event.aggregateId());
            }
        });

        kafkaTemplate.send(TOPIC, payload);
    }

}
