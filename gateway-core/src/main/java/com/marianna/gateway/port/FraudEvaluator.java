package com.marianna.gateway.port;

import com.marianna.gateway.domain.FraudSignal;
import com.marianna.gateway.domain.PaymentOrder;

public interface FraudEvaluator {
    FraudSignal evaluate(PaymentOrder order);
}
