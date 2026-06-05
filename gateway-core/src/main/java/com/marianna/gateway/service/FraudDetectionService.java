package com.marianna.gateway.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.marianna.gateway.domain.FraudSignal;
import com.marianna.gateway.domain.PaymentMethod;
import com.marianna.gateway.domain.PaymentOrder;
import com.marianna.gateway.port.FraudEvaluator;

@Service
public class FraudDetectionService implements FraudEvaluator {

    private static final BigDecimal HIGH = new BigDecimal("5000.00");
    private static final BigDecimal VERY_HIGH = new BigDecimal("10000.00");

    @Override
    public FraudSignal evaluate(PaymentOrder order) {
        int risk = 0;
        List<String> flags = new ArrayList<>();

        if (order.amount().compareTo(VERY_HIGH) > 0) {
            risk += 75;
            flags.add("VERY_HIGH_AMOUNT");
        } else if (order.amount().compareTo(HIGH) > 0) {
            risk += 25;
            flags.add("HIGH_AMOUNT");
        }

        if (PaymentMethod.WALLET.equals(order.method()) && order.amount().compareTo(HIGH) > 0) {
            risk += 15;
            flags.add("HIGH_WALLET_AMOUNT");
        }

        // TODO Week 3: add Redis velocity check
        return flags.isEmpty() ? FraudSignal.clean(order.id())
                : FraudSignal.risky(order.id(), risk, flags);
    }
}
