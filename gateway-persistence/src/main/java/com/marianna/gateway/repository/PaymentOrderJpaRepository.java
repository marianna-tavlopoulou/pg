package com.marianna.gateway.repository;

import com.marianna.gateway.domain.PaymentStatus;
import com.marianna.gateway.entity.PaymentOrderEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentOrderJpaRepository extends JpaRepository<PaymentOrderEntity, UUID> {
    Optional<PaymentOrderEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentOrderEntity> findByIdempotencyKeyAndVersion(String idempotencyKey, Long version);

    List<PaymentOrderEntity> findByMerchantIdAndStatus(UUID merchantId, PaymentStatus status);
}
