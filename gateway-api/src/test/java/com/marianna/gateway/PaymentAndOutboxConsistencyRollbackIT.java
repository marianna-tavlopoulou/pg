package com.marianna.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.marianna.gateway.domain.Currency;
import com.marianna.gateway.domain.PaymentEvent;
import com.marianna.gateway.domain.PaymentMethod;
import com.marianna.gateway.domain.PaymentStatus;
import com.marianna.gateway.dto.PaymentRequest;
import com.marianna.gateway.dto.PaymentResponse;
import com.marianna.gateway.port.OutboxRepository;
import com.marianna.gateway.port.PaymentRepository;

@Import(OutboxInsertionFailureConfig.class)
class PaymentAndOutboxConsistencyRollbackIT extends BaseIntegrationTest {

    @Autowired
    OutboxRepository outboxRepository;

    @Autowired
    PaymentRepository paymentRepository;

    /*
     * This test showed that when the outboxRepository.save() method throws an
     * exception, the http request fails
     * but we need to handle this exception and return 201 to the client. The
     * payment_orders.status should be rolled back to PENDING.
     */
    @Test
    @DisplayName("When a payment order is created and outboxRepository.save() throws an exception, payment_orders.status should be rolled back to PENDING.")
    void shouldRollBackPaymentOrderStatusWhenOutboxEventInsertionFails() throws Exception {

        UUID customerId = UUID.randomUUID();

        PaymentResponse response = createPayment(
                new PaymentRequest(customerId, new BigDecimal(9000), Currency.EUR, PaymentMethod.CARD,
                        "order #1"),
                UUID.randomUUID().toString());

        List<PaymentEvent> events = outboxRepository.findUnpublishedEvents(10);
        assertThat(events).isEmpty();

        PaymentResponse response2 = getPayment(response.id().toString());
        assertThat(response2.status()).isEqualTo(PaymentStatus.PENDING);
    }

}
