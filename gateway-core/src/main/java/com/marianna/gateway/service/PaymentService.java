package com.marianna.gateway.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marianna.gateway.domain.FraudSignal;
import com.marianna.gateway.domain.PaymentOrder;
import com.marianna.gateway.domain.PaymentStatus;
import com.marianna.gateway.port.FraudEvaluator;
import com.marianna.gateway.port.PaymentRepository;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final FraudEvaluator fraudEvaluator;

    public PaymentService(PaymentRepository paymentRepository, FraudEvaluator fraudEvaluator) {
        this.paymentRepository = paymentRepository;
        this.fraudEvaluator = fraudEvaluator;
    }

    @Transactional
    public PaymentOrder submit(PaymentOrder order) {
        // Idempotency: return existing result if key seen before

        PaymentOrder saved;
        try {
            saved = paymentRepository.save(order);
        } catch (DataIntegrityViolationException e) {
            return paymentRepository.findByIdempotencyKey(order.idempotencyKey())
                    .orElseThrow(() -> new DataIntegrityViolationException(
                            "Duplicate key detected but existing record could not be retrieved", e));
        }
        FraudSignal signal = fraudEvaluator.evaluate(saved);

        if (signal.shouldDecline()) {
            return paymentRepository.save(saved.withStatus(PaymentStatus.DECLINED)); // DECLINE: score >= 70
        }

        if (signal.riskScore() >= 40) {
            return paymentRepository.save(saved.withStatus(PaymentStatus.PROCESSING)); // REVIEW: 40 <= score < 70
        }

        return paymentRepository.save(saved.withStatus(PaymentStatus.COMPLETED));
    }

    public Optional<PaymentOrder> findPaymentByIdForMerchant(UUID id, UUID merchantId) {
        Optional<PaymentOrder> orderOptional = paymentRepository.findById(id);
        if (orderOptional.isPresent() && orderOptional.get().merchantId().equals(merchantId)) {
            return orderOptional;
        }
        return Optional.empty();
    }
}
