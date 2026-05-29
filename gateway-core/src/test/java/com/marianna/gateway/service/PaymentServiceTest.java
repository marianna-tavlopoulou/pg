package com.marianna.gateway.service;

import com.marianna.gateway.domain.*;
import com.marianna.gateway.port.FraudEvaluator;
import com.marianna.gateway.port.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock FraudEvaluator fraudEvaluator;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, fraudEvaluator);
    }

    @Test
    @DisplayName("Clean payment via card should complete successfully")
    void shouldCompleteCleanPaymentByCard() {
        PaymentOrder order = buildOrder(new BigDecimal("100.00"), PaymentMethod.CARD);
        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(fraudEvaluator.evaluate(any())).thenReturn(FraudSignal.clean(order.id()));

        PaymentOrder result = paymentService.submit(order);

        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentRepository, times(3)).save(any());
    }

    @Test
    @DisplayName("Clean payment via bank transfer should complete successfully")
    void shouldCompleteCleanPaymentByBankTransfer() {
        PaymentOrder order = buildOrder(new BigDecimal("100.00"), PaymentMethod.BANK_TRANSFER);
        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(fraudEvaluator.evaluate(any())).thenReturn(FraudSignal.clean(order.id()));

        PaymentOrder result = paymentService.submit(order);

        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentRepository, times(3)).save(any());
    }

    @Test
    @DisplayName("High-risk payment should be declined")
    void shouldDeclineHighRiskPayment() {
        PaymentOrder order = buildOrder(new BigDecimal("15000.00"), PaymentMethod.CARD);
        FraudSignal risky = FraudSignal.risky(order.id(), 80, List.of("VERY_HIGH_AMOUNT"));
        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(fraudEvaluator.evaluate(any())).thenReturn(risky);

        assertThat(paymentService.submit(order).status()).isEqualTo(PaymentStatus.DECLINED);
    }

    @Test
    @DisplayName("Duplicate idempotency key returns existing payment without reprocessing")
    void shouldReturnExistingOnDuplicateKey() {
        PaymentOrder order = buildOrder(new BigDecimal("50.00"), PaymentMethod.CARD);
        PaymentOrder completed = order.withStatus(PaymentStatus.PROCESSING).withStatus(PaymentStatus.COMPLETED);
        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(completed));

        assertThat(paymentService.submit(order).status()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentRepository, never()).save(any());
        verify(fraudEvaluator, never()).evaluate(any());
    }

    private PaymentOrder buildOrder(BigDecimal amount, PaymentMethod paymentMethod) {
        return PaymentOrder.create(UUID.randomUUID(), amount, Currency.EUR,
                paymentMethod, UUID.randomUUID().toString(), "Test");
    }
}
