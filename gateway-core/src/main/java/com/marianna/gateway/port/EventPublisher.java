package com.marianna.gateway.port;

import com.marianna.gateway.domain.PaymentEvent;

public interface EventPublisher {

    void publish(PaymentEvent event, String customerId);

}
