package com.marianna.gateway.port;

import java.math.BigDecimal;
import java.time.Duration;

public interface VelocityCheckPort {

    int getTransactionCounts(String customerId, Duration window);

    void recordTransaction(String customerId, String transactionId);

    int getDuplicateAmountCount(String customerId, BigDecimal amount, Duration window);

    boolean isVelocityExceeded(String customerId);

}
