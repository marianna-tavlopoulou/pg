package com.marianna.gateway.step;

import com.marianna.gateway.domain.Currency;

public record SettlementRecord(
        String paymentId, String merchantId,
        java.math.BigDecimal amount, Currency currency,
        String method, String processedAt) {
}
