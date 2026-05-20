package com.marianna.gateway.exception;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(UUID id) {
        super("Payment %s not found".formatted(id));
    }
}
