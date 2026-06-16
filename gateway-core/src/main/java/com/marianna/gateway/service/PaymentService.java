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

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final FraudEvaluator fraudEvaluator;

    public PaymentService(PaymentRepository paymentRepository, FraudEvaluator fraudEvaluator) {
        this.paymentRepository = paymentRepository;
        this.fraudEvaluator = fraudEvaluator;
    }

    public PaymentOrder submit(PaymentOrder order) {
        // Idempotency: return existing result if key seen before

        PaymentOrder saved;
        try {
            saved = saveNewOrder(order);
        } catch (DataIntegrityViolationException e) {
            return findExistingOrder(order);
        }

        return finalizePaymentStatus(saved);
    }

    public Optional<PaymentOrder> findPaymentByIdForMerchant(UUID id, UUID merchantId) {
        Optional<PaymentOrder> orderOptional = paymentRepository.findById(id);
        if (orderOptional.isPresent() && orderOptional.get().merchantId().equals(merchantId)) {
            return orderOptional;
        }
        return Optional.empty();
    }

    @Transactional
    private PaymentOrder findExistingOrder(PaymentOrder order) {
        return paymentRepository.findByIdempotencyKey(order.idempotencyKey())
                .orElseThrow(() -> new DataIntegrityViolationException(
                        "Duplicate key detected but existing record could not be retrieved"));
    }

    @Transactional
    private PaymentOrder saveNewOrder(PaymentOrder order) {
        return paymentRepository.saveAndFlush(order);
    }

    @Transactional
    private PaymentOrder finalizePaymentStatus(PaymentOrder saved) {
        FraudSignal signal = fraudEvaluator.evaluate(saved);

        if (signal.shouldDecline()) {
            log.debug("Customer: {} | payment id: {}, DECLINED", saved.customerId(), saved.id());
            return paymentRepository.save(saved.withStatus(PaymentStatus.DECLINED)); // DECLINE: score >= 70
        }

        if (signal.riskScore() >= 40) {
            log.debug("Customer: {} | payment id: {}, REVIEW", saved.customerId(), saved.id());
            return paymentRepository.save(saved.withStatus(PaymentStatus.PROCESSING)); // REVIEW: 40 <= score < 70
        }

        log.debug("Customer: {} | payment id: {}, CLEAN", saved.customerId(), saved.id());
        return paymentRepository.save(saved.withStatus(PaymentStatus.COMPLETED));
    }
}
