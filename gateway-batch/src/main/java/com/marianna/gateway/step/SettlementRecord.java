package com.marianna.gateway.step;

public record SettlementRecord(
    String paymentId, String merchantId,
    java.math.BigDecimal amount, String currency,
    String method, String processedAt
) {}
