package com.marianna.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, fraudEvaluator);
    }

    @Test
    @DisplayName("Clean payment via card should complete successfully")
    void shouldCompleteCleanPaymentByCard() {
        PaymentOrder order = buildOrder(new BigDecimal("100.00"), PaymentMethod.CARD);
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(fraudEvaluator.evaluate(any())).thenReturn(FraudSignal.clean(order.id()));

        PaymentOrder result = paymentService.submit(order);

        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Clean payment via bank transfer should complete successfully")
    void shouldCompleteCleanPaymentByBankTransfer() {
        PaymentOrder order = buildOrder(new BigDecimal("100.00"), PaymentMethod.BANK_TRANSFER);
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(fraudEvaluator.evaluate(any())).thenReturn(FraudSignal.clean(order.id()));

        PaymentOrder result = paymentService.submit(order);

        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("High-risk payment should be declined")
    void shouldDeclineHighRiskPayment() {
        PaymentOrder order = buildOrder(new BigDecimal("15000.00"), PaymentMethod.CARD);
        FraudSignal risky = FraudSignal.risky(order.id(), 80, List.of("VERY_HIGH_AMOUNT"));
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(fraudEvaluator.evaluate(any())).thenReturn(risky);

        assertThat(paymentService.submit(order).status()).isEqualTo(PaymentStatus.DECLINED);
    }

    @Test
    @DisplayName("Duplicate idempotency key returns existing payment without reprocessing")
    void shouldReturnExistingOnDuplicateKey() {

        PaymentOrder incomingOrder = buildOrder(new BigDecimal("50.00"), PaymentMethod.CARD);
        PaymentOrder existingOrder = buildOrder(new BigDecimal("50.00"), PaymentMethod.CARD)
                .withStatus(PaymentStatus.COMPLETED);

        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(existingOrder));
        when(paymentRepository.save(incomingOrder)).thenThrow(new DataIntegrityViolationException("already exists"));

        assertThat(paymentService.submit(incomingOrder).status()).isEqualTo(PaymentStatus.COMPLETED);
        verify(fraudEvaluator, never()).evaluate(any());
    }

    private PaymentOrder buildOrder(BigDecimal amount, PaymentMethod paymentMethod) {
        return PaymentOrder.create(UUID.randomUUID(), UUID.randomUUID(), amount, Currency.EUR,
                paymentMethod, UUID.randomUUID().toString(), "Test");
    }
}
