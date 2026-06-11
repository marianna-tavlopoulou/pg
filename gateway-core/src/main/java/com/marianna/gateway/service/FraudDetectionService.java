package com.marianna.gateway.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.marianna.gateway.domain.FraudSignal;
import com.marianna.gateway.domain.PaymentMethod;
import com.marianna.gateway.domain.PaymentOrder;
import com.marianna.gateway.port.FraudEvaluator;
import com.marianna.gateway.port.VelocityCheckPort;

@Service
public class FraudDetectionService implements FraudEvaluator {

    private final VelocityCheckPort velocityCheckPort;

    private static final BigDecimal HIGH = new BigDecimal("5000.00");
    private static final BigDecimal VERY_HIGH = new BigDecimal("10000.00");

    // Thresholds: review at 40, decline at 70
    private static final int VELOCITY_SCORE = 30;
    private static final int HIGH_AMOUNT_SCORE = 25;
    private static final int VERY_HIGH_AMOUNT_SCORE = 75;
    private static final int HIGH_WALLET_SCORE = 15;

    public FraudDetectionService(VelocityCheckPort velocityCheckPort) {
        this.velocityCheckPort = velocityCheckPort;
    }

    @Override
    public FraudSignal evaluate(PaymentOrder order) {
        int risk = 0;
        List<String> flags = new ArrayList<>();

        // --- Signal 1 & 2: Amount tier ---
        if (order.amount().compareTo(VERY_HIGH) > 0) {
            risk += VERY_HIGH_AMOUNT_SCORE;
            flags.add("VERY_HIGH_AMOUNT");
        } else if (order.amount().compareTo(HIGH) > 0) {
            risk += HIGH_AMOUNT_SCORE;
            flags.add("HIGH_AMOUNT");
        }

        // --- Signal 3: High-value wallet payment ---
        if (PaymentMethod.WALLET.equals(order.method()) && order.amount().compareTo(HIGH) > 0) {
            risk += HIGH_WALLET_SCORE;
            flags.add("HIGH_WALLET_AMOUNT");
        }

        if (velocityCheckPort.isVelocityExceeded(order.customerId().toString())) {
            risk += VELOCITY_SCORE;
            flags.add("VELOCITY_EXCEEDED");
        }

        if (velocityCheckPort.getDuplicateAmountCount(order.customerId().toString(), order.amount()) >= 2) {
            risk += VELOCITY_SCORE;
            flags.add("DUPLICATE_AMOUNT");
        }
        return flags.isEmpty() ? FraudSignal.clean(order.id()) // the score is also zero
                : FraudSignal.risky(order.id(), risk, flags);
    }
}
