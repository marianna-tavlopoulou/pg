package com.marianna.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.marianna.gateway.domain.Currency;
import com.marianna.gateway.domain.FraudSignal;
import com.marianna.gateway.domain.PaymentMethod;
import com.marianna.gateway.domain.PaymentOrder;
import com.marianna.gateway.domain.PaymentStatus;
import com.marianna.gateway.port.FraudEvaluator;
import com.marianna.gateway.port.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    PaymentRepository paymentRepository;
    @Mock
    FraudEvaluator fraudEvaluator;
    @Mock
    PaymentFinalizationService paymentFinalizationService;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, fraudEvaluator, paymentFinalizationService);
    }

    @Test
    @DisplayName("Clean payment via card should complete successfully")
    void shouldCompleteCleanPaymentByCard() {
        PaymentOrder order = buildOrder(new BigDecimal("100.00"), PaymentMethod.CARD);
        PaymentOrder finalizedOrder = finalizeOrder(order, PaymentStatus.COMPLETED);
        when(paymentRepository.saveAndFlush(any(PaymentOrder.class))).thenAnswer(i -> i.getArgument(0));
        when(fraudEvaluator.evaluate(any(PaymentOrder.class))).thenReturn(FraudSignal.clean(order.id()));
        when(paymentFinalizationService.finalizePaymentStatus(any(PaymentOrder.class),
                eq(FraudSignal.clean(order.id()))))
                .thenReturn(finalizedOrder);

        PaymentOrder result = paymentService.submit(order);

        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentRepository, times(1)).saveAndFlush(any(PaymentOrder.class));
        verify(paymentFinalizationService, times(1)).finalizePaymentStatus(any(PaymentOrder.class),
                any(FraudSignal.class));
    }

    @Test
    @DisplayName("Clean payment via bank transfer should complete successfully")
    void shouldCompleteCleanPaymentByBankTransfer() {
        PaymentOrder order = buildOrder(new BigDecimal("100.00"), PaymentMethod.BANK_TRANSFER);
        PaymentOrder finalizedOrder = finalizeOrder(order, PaymentStatus.COMPLETED);
        when(paymentRepository.saveAndFlush(any(PaymentOrder.class))).thenAnswer(i -> i.getArgument(0));
        when(fraudEvaluator.evaluate(any(PaymentOrder.class))).thenReturn(FraudSignal.clean(order.id()));
        when(paymentFinalizationService.finalizePaymentStatus(any(PaymentOrder.class),
                eq(FraudSignal.clean(order.id()))))
                .thenReturn(finalizedOrder);

        PaymentOrder result = paymentService.submit(order);

        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentFinalizationService, times(1)).finalizePaymentStatus(any(PaymentOrder.class),
                any(FraudSignal.class));
        verify(paymentRepository, never()).save(any(PaymentOrder.class));
        verify(paymentRepository, times(1)).saveAndFlush(any(PaymentOrder.class));
    }

    @Test
    @DisplayName("High-risk payment should be declined")
    void shouldDeclineHighRiskPayment() {
        PaymentOrder order = buildOrder(new BigDecimal("15000.00"), PaymentMethod.CARD);
        PaymentOrder finalizedOrder = finalizeOrder(order, PaymentStatus.DECLINED);
        when(paymentFinalizationService.finalizePaymentStatus(any(PaymentOrder.class), any(FraudSignal.class)))
                .thenReturn(finalizedOrder);
        FraudSignal risky = FraudSignal.risky(order.id(), 80, List.of("VERY_HIGH_AMOUNT"));
        when(paymentRepository.saveAndFlush(any(PaymentOrder.class))).thenAnswer(i -> i.getArgument(0));
        when(fraudEvaluator.evaluate(any(PaymentOrder.class))).thenReturn(risky);

        assertThat(paymentService.submit(order).status()).isEqualTo(PaymentStatus.DECLINED);
        verify(paymentFinalizationService, times(1)).finalizePaymentStatus(any(PaymentOrder.class),
                any(FraudSignal.class));
        verify(paymentRepository, times(1)).saveAndFlush(any(PaymentOrder.class));
        verify(paymentRepository, never()).save(any(PaymentOrder.class));
    }

    @Test
    @DisplayName("Duplicate idempotency key returns existing payment without reprocessing")
    void shouldReturnExistingOnDuplicateKey() {

        PaymentOrder incomingOrder = buildOrder(new BigDecimal("50.00"), PaymentMethod.CARD)
                .withStatus(PaymentStatus.COMPLETED);
        PaymentOrder existingOrder = new PaymentOrder(incomingOrder.id(), incomingOrder.customerId(),
                incomingOrder.merchantId(), incomingOrder.amount(), incomingOrder.currency(), incomingOrder.method(),
                incomingOrder.status(), incomingOrder.idempotencyKey(), incomingOrder.description(),
                incomingOrder.createdAt(), incomingOrder.updatedAt(), 0L);

        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(existingOrder));
        when(paymentRepository.saveAndFlush(incomingOrder))
                .thenThrow(new DataIntegrityViolationException("already exists"));

        paymentService.submit(incomingOrder);

        assertThat(paymentService.submit(incomingOrder).status()).isEqualTo(PaymentStatus.COMPLETED);

        verify(fraudEvaluator, never()).evaluate(any());
        verify(paymentFinalizationService, never()).finalizePaymentStatus(existingOrder, null);
    }

    private PaymentOrder buildOrder(BigDecimal amount, PaymentMethod paymentMethod) {
        return PaymentOrder.create(UUID.randomUUID(), UUID.randomUUID(), amount, Currency.EUR,
                paymentMethod, UUID.randomUUID().toString(), "Test");
    }

    private PaymentOrder finalizeOrder(PaymentOrder order, PaymentStatus status) {
        return new PaymentOrder(order.id(), order.customerId(), order.merchantId(), order.amount(), order.currency(),
                order.method(), status, order.idempotencyKey(), order.description(), order.createdAt(),
                Instant.now(), 0L);
    }
}
