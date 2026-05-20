package com.marianna.gateway.domain;

import java.util.List;
import java.util.UUID;

public record FraudSignal(UUID paymentId, int riskScore, List<String> flags, boolean shouldDecline) {
    public static final int DECLINE_THRESHOLD = 70;

    public static FraudSignal clean(UUID paymentId) {
        return new FraudSignal(paymentId, 0, List.of(), false);
    }

    public static FraudSignal risky(UUID paymentId, int score, List<String> flags) {
        return new FraudSignal(paymentId, score, flags, score >= DECLINE_THRESHOLD);
    }
}
