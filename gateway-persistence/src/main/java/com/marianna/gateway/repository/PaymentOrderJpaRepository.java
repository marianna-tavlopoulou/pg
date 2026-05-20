package com.marianna.gateway.repository;

import com.marianna.gateway.domain.PaymentStatus;
import com.marianna.gateway.entity.PaymentOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentOrderJpaRepository extends JpaRepository<PaymentOrderEntity, UUID> {
    Optional<PaymentOrderEntity> findByIdempotencyKey(String idempotencyKey);
    List<PaymentOrderEntity> findByMerchantIdAndStatus(UUID merchantId, PaymentStatus status);
}
