package com.marianna.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.marianna.gateway.domain.Currency;
import com.marianna.gateway.domain.FraudSignal;
import com.marianna.gateway.domain.PaymentMethod;
import com.marianna.gateway.domain.PaymentOrder;

@ExtendWith(MockitoExtension.class)
public class FraudDetectionServiceTest {

    private FraudDetectionService fraudDetectionService;

    @BeforeEach
    void setUp() {
        fraudDetectionService = new FraudDetectionService();    
    }

    @Test
    @DisplayName("The risk score is 25+15=40, which is below the 70 decline threshold — so the payment completes but is flagged.")
    void shouldFlagWalletWithHighAmount() {

        PaymentOrder order = PaymentOrder.create(UUID.randomUUID(), new BigDecimal(5500), Currency.EUR, PaymentMethod.WALLET, "idem-stub-key-0001", "test wallet with high amount");
        FraudSignal fraudSignal = fraudDetectionService.evaluate(order);
        assertThat(fraudSignal.flags()).hasSize(2).contains("HIGH_AMOUNT", "HIGH_WALLET_AMOUNT");
        assertFalse(fraudSignal.shouldDecline());

    }

}
