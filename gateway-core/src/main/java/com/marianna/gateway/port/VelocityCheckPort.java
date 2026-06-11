package com.marianna.gateway.port;

import java.math.BigDecimal;
import java.time.Duration;

public interface VelocityCheckPort {

    int getTransactionCounts(String customerId, Duration window);

    void recordTransaction(String customerId, String transactionId, BigDecimal amount);

    int getDuplicateAmountCount(String customerId, BigDecimal amount);

    boolean isVelocityExceeded(String customerId);

}
