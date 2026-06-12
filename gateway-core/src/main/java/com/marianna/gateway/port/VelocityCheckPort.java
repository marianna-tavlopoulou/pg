package com.marianna.gateway.port;

import java.math.BigDecimal;

public interface VelocityCheckPort {

    int getTransactionCounts(String customerId);

    void recordTransaction(String customerId, String transactionId, BigDecimal amount);

    int getDuplicateAmountCount(String customerId, BigDecimal amount);

    boolean isVelocityExceeded(String customerId);

}
