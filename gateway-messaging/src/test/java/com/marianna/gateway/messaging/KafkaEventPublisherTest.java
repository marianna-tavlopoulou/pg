package com.marianna.gateway.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marianna.gateway.domain.Currency;
import com.marianna.gateway.domain.PaymentEvent;
import com.marianna.gateway.domain.PaymentEventType;
import com.marianna.gateway.domain.PaymentMethod;
import com.marianna.gateway.domain.PaymentOrder;
import com.marianna.gateway.domain.PaymentStatus;

@ExtendWith(MockitoExtension.class)
@Disabled
class KafkaEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaEventPublisher kafkaEventPublisher;
    private ObjectMapper objectMapper;
    @Value("${gateway.messaging.payment-events-topic}")
    private String topic;
    private String jsonPaymentEvent = "";

    @BeforeEach
    void setUp() {
        kafkaEventPublisher = new KafkaEventPublisher(kafkaTemplate, topic, objectMapper);
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should publish event to Kafka with customerId as the routing partition key")
    void shouldPublishWithCustomerIdAsPartitionKey() throws Exception {
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

    }

}