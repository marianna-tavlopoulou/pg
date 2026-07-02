package com.marianna.gateway.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marianna.gateway.domain.Currency;
import com.marianna.gateway.domain.PaymentMethod;
import com.marianna.gateway.domain.PaymentOrder;
import com.marianna.gateway.port.OutboxRepository;
import com.marianna.gateway.port.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentFinalizationServiceTest {

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    OutboxRepository outboxRepository;
    @Mock
    ObjectMapper objectMapper;

    private PaymentFinalizationService paymentFinalizationService;

    @BeforeEach
    void setUp() {
        paymentFinalizationService = new PaymentFinalizationService(paymentRepository,
                outboxRepository, objectMapper);
    }

    @Test
    @DisplayName("Clean payment via card should complete successfully")
    void shouldCompleteCleanPaymentByCard() {
        PaymentOrder order = buildOrder(new BigDecimal("100.00"), PaymentMethod.CARD, null);
    }

    private PaymentOrder buildOrder(BigDecimal amount, PaymentMethod paymentMethod, Long version) {
        return PaymentOrder.create(UUID.randomUUID(), UUID.randomUUID(), amount, Currency.EUR,
                paymentMethod, UUID.randomUUID().toString(), "Test");
    }

}
