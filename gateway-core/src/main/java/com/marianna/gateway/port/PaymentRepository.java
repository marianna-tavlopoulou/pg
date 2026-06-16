package com.marianna.gateway.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.marianna.gateway.domain.PaymentOrder;
import com.marianna.gateway.domain.PaymentStatus;

public interface PaymentRepository {
    PaymentOrder save(PaymentOrder order);

    PaymentOrder saveAndFlush(PaymentOrder order);

    Optional<PaymentOrder> findById(UUID id);

    Optional<PaymentOrder> findByIdempotencyKey(String idempotencyKey);

    List<PaymentOrder> findByMerchantIdAndStatus(UUID merchantId, PaymentStatus status);
}
