package com.marianna.gateway.service;

import com.marianna.gateway.domain.FraudSignal;
import com.marianna.gateway.domain.PaymentOrder;
import com.marianna.gateway.domain.PaymentStatus;
import com.marianna.gateway.port.FraudEvaluator;
import com.marianna.gateway.port.PaymentRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final FraudEvaluator fraudEvaluator;

    public PaymentService(PaymentRepository paymentRepository, FraudEvaluator fraudEvaluator) {
        this.paymentRepository = paymentRepository;
        this.fraudEvaluator = fraudEvaluator;
    }

    public PaymentOrder submit(PaymentOrder order) {
        // Idempotency: return existing result if key seen before
        Optional<PaymentOrder> existing = paymentRepository.findByIdempotencyKey(order.idempotencyKey());
        if (existing.isPresent()) return existing.get();

        PaymentOrder saved = paymentRepository.save(order);
        FraudSignal signal = fraudEvaluator.evaluate(saved);

        if (signal.shouldDecline()) {
            return paymentRepository.save(saved.withStatus(PaymentStatus.DECLINED));
        }

        PaymentOrder processing = paymentRepository.save(saved.withStatus(PaymentStatus.PROCESSING));
        return paymentRepository.save(processing.withStatus(PaymentStatus.COMPLETED));
    }

    public Optional<PaymentOrder> findById(UUID id) {
        return paymentRepository.findById(id);
    }
}
