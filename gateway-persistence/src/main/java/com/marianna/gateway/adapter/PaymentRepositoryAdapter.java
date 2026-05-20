package com.marianna.gateway.adapter;

import com.marianna.gateway.domain.PaymentOrder;
import com.marianna.gateway.domain.PaymentStatus;
import com.marianna.gateway.entity.PaymentOrderEntity;
import com.marianna.gateway.port.PaymentRepository;
import com.marianna.gateway.repository.PaymentOrderJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PaymentRepositoryAdapter implements PaymentRepository {

    private final PaymentOrderJpaRepository jpa;

    public PaymentRepositoryAdapter(PaymentOrderJpaRepository jpa) { this.jpa = jpa; }

    @Override public PaymentOrder save(PaymentOrder o) { return toDomain(jpa.save(toEntity(o))); }
    @Override public Optional<PaymentOrder> findById(UUID id) { return jpa.findById(id).map(this::toDomain); }
    @Override public Optional<PaymentOrder> findByIdempotencyKey(String key) { return jpa.findByIdempotencyKey(key).map(this::toDomain); }
    @Override public List<PaymentOrder> findByMerchantIdAndStatus(UUID mid, PaymentStatus s) {
        return jpa.findByMerchantIdAndStatus(mid, s).stream().map(this::toDomain).toList();
    }

    private PaymentOrderEntity toEntity(PaymentOrder o) {
        var e = new PaymentOrderEntity();
        e.setId(o.id()); e.setMerchantId(o.merchantId()); e.setAmount(o.amount());
        e.setCurrency(o.currency()); e.setMethod(o.method()); e.setStatus(o.status());
        e.setIdempotencyKey(o.idempotencyKey()); e.setDescription(o.description());
        e.setCreatedAt(o.createdAt()); e.setUpdatedAt(o.updatedAt());
        return e;
    }

    private PaymentOrder toDomain(PaymentOrderEntity e) {
        return new PaymentOrder(e.getId(), e.getMerchantId(), e.getAmount(), e.getCurrency(),
            e.getMethod(), e.getStatus(), e.getIdempotencyKey(), e.getDescription(),
            e.getCreatedAt(), e.getUpdatedAt());
    }
}
