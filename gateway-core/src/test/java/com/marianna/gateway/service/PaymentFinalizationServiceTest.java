package com.marianna.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.marianna.gateway.domain.Currency;
import com.marianna.gateway.domain.FraudSignal;
import com.marianna.gateway.domain.PaymentEvent;
import com.marianna.gateway.domain.PaymentEventType;
import com.marianna.gateway.domain.PaymentMethod;
import com.marianna.gateway.domain.PaymentOrder;
import com.marianna.gateway.domain.PaymentStatus;
import com.marianna.gateway.port.OutboxRepository;
import com.marianna.gateway.port.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentFinalizationServiceTest {

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    OutboxRepository outboxRepository;
    ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private PaymentFinalizationService paymentFinalizationService;

    @BeforeEach
    void setUp() {
        paymentFinalizationService = new PaymentFinalizationService(paymentRepository,
                outboxRepository, objectMapper);
    }

    @Test
    @DisplayName("Clean payment via card should complete successfully")
    void shouldCompleteCleanPaymentByCard() throws Exception {

        PaymentOrder order = buildOrder(new BigDecimal("100.00"), PaymentMethod.CARD);
        FraudSignal fraudSignal = FraudSignal.clean(order.id());

        when(paymentRepository.save(any(PaymentOrder.class))).thenReturn(order);

        PaymentOrder result = paymentFinalizationService.finalizePaymentStatus(order, fraudSignal);

        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);

        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(outboxRepository, times(1)).save(eventCaptor.capture());
        PaymentEvent capturedEvent = eventCaptor.getValue();
        PaymentOrder savedOrder = capturedEvent.payload() != null
                ? objectMapper.readValue(capturedEvent.payload(), PaymentOrder.class)
                : null;
        assertThat(capturedEvent.aggregateId()).isEqualTo(order.id());
        assertThat(capturedEvent.eventType()).isEqualTo(PaymentEventType.PAYMENT_COMPLETED);
        assertThat(capturedEvent.eventId()).isNotNull();
        assertThat(capturedEvent.occurredAt()).isNotNull();
        assertThat(savedOrder).usingRecursiveComparison().isEqualTo(result);

        ArgumentCaptor<PaymentOrder> orderCaptor = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(paymentRepository, times(1)).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().status()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("Risky payment should be declined")
    void shouldDeclinePayment() throws Exception {

        PaymentOrder order = buildOrder(new BigDecimal("15000.00"), PaymentMethod.CARD);
        FraudSignal risky = FraudSignal.risky(order.id(), 80, List.of("VERY_HIGH_AMOUNT"));

        when(paymentRepository.save(any(PaymentOrder.class))).thenReturn(order);

        PaymentOrder result = paymentFinalizationService.finalizePaymentStatus(order, risky);

        assertThat(result.status()).isEqualTo(PaymentStatus.DECLINED);

        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(outboxRepository, times(1)).save(eventCaptor.capture());
        PaymentEvent capturedEvent = eventCaptor.getValue();
        PaymentOrder savedOrder = capturedEvent.payload() != null
                ? objectMapper.readValue(capturedEvent.payload(), PaymentOrder.class)
                : null;
        assertThat(capturedEvent.aggregateId()).isEqualTo(order.id());
        assertThat(capturedEvent.eventType()).isEqualTo(PaymentEventType.PAYMENT_DECLINED);
        assertThat(capturedEvent.eventId()).isNotNull();
        assertThat(capturedEvent.occurredAt()).isNotNull();
        assertThat(savedOrder).usingRecursiveComparison().isEqualTo(result);

        ArgumentCaptor<PaymentOrder> orderCaptor = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(paymentRepository, times(1)).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().status()).isEqualTo(PaymentStatus.DECLINED);
    }

    @Test
    @DisplayName("Payment with score > 40 should be reviewed (processing)")
    void shouldReviewPayment() throws Exception {

        PaymentOrder order = buildOrder(new BigDecimal("15000.00"), PaymentMethod.CARD);
        FraudSignal risky = FraudSignal.risky(order.id(), 45, List.of("VERY_HIGH_AMOUNT"));

        when(paymentRepository.save(any(PaymentOrder.class))).thenReturn(order);

        PaymentOrder result = paymentFinalizationService.finalizePaymentStatus(order, risky);

        assertThat(result.status()).isEqualTo(PaymentStatus.PROCESSING);

        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(outboxRepository, times(1)).save(eventCaptor.capture());
        PaymentEvent capturedEvent = eventCaptor.getValue();
        PaymentOrder savedOrder = capturedEvent.payload() != null
                ? objectMapper.readValue(capturedEvent.payload(), PaymentOrder.class)
                : null;
        assertThat(capturedEvent.aggregateId()).isEqualTo(order.id());
        assertThat(capturedEvent.eventType()).isEqualTo(PaymentEventType.PAYMENT_PROCESSING);
        assertThat(capturedEvent.eventId()).isNotNull();
        assertThat(capturedEvent.occurredAt()).isNotNull();
        assertThat(savedOrder).usingRecursiveComparison().isEqualTo(result);

        ArgumentCaptor<PaymentOrder> orderCaptor = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(paymentRepository, times(1)).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().status()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    @DisplayName("When paymentRepository.save() throws → outboxRepository.save() never called (proves transaction rollback intent even in unit context)")
    void shouldRollbackTransactionWhenPaymentRepositorySaveThrows() throws Exception {

        PaymentOrder order = buildOrder(new BigDecimal("100.00"), PaymentMethod.CARD);
        FraudSignal fraudSignal = FraudSignal.clean(order.id());

        when(paymentRepository.save(any(PaymentOrder.class))).thenThrow(new RuntimeException("Database error"));

        assertThrows(RuntimeException.class,
                () -> paymentFinalizationService.finalizePaymentStatus(order, fraudSignal));
        verifyNoInteractions(outboxRepository);
    }

    private PaymentOrder buildOrder(BigDecimal amount, PaymentMethod paymentMethod) {
        return PaymentOrder.create(UUID.randomUUID(), UUID.randomUUID(), amount, Currency.EUR,
                paymentMethod, UUID.randomUUID().toString(), "Test");
    }

}
