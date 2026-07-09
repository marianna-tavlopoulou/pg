package com.marianna.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.marianna.gateway.domain.Currency;
import com.marianna.gateway.domain.PaymentEvent;
import com.marianna.gateway.domain.PaymentEventType;
import com.marianna.gateway.domain.PaymentMethod;
import com.marianna.gateway.domain.PaymentStatus;
import com.marianna.gateway.dto.PaymentRequest;
import com.marianna.gateway.dto.PaymentResponse;
import com.marianna.gateway.port.OutboxRepository;
import com.marianna.gateway.port.PaymentRepository;

class PaymentAndOutboxConsistencyHappyPathIT extends BaseIntegrationTest {

    @Autowired
    OutboxRepository outboxRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Test
    @DisplayName("When a payment order is created, an outbox event should be inserted in the outbox table.")
    void shouldStoreOutboxEventWhenPaymentOrderIsCreated() throws Exception {
        PaymentResponse response = createPayment(
                new PaymentRequest(UUID.randomUUID(), new BigDecimal(9000), Currency.EUR, PaymentMethod.CARD,
                        "order #1"),
                UUID.randomUUID().toString());
        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        Optional<PaymentEvent> event = outboxRepository.findByAggregateId(response.id());
        assertThat(event).isPresent();
        assertThat(event.get().aggregateId()).isEqualTo(response.id());
        assertThat(event.get().eventType()).isEqualTo(PaymentEventType.PAYMENT_COMPLETED);
        assertThat(event.get().payload()).contains(response.id().toString());
    }

}