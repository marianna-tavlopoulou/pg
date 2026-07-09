package com.marianna.gateway.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.marianna.gateway.domain.PaymentOrder;
import com.marianna.gateway.port.FraudEvaluator;
import com.marianna.gateway.port.PaymentRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentFinalizationService paymentFinalizationService;
    private final FraudEvaluator fraudEvaluator;

    public PaymentService(PaymentRepository paymentRepository, FraudEvaluator fraudEvaluator,
            PaymentFinalizationService paymentFinalizationService) {
        this.paymentRepository = paymentRepository;
        this.paymentFinalizationService = paymentFinalizationService;
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

        try {
            return paymentFinalizationService.finalizePaymentStatus(saved, fraudEvaluator.evaluate(saved));
        } catch (Exception e) {
            // Finalization failed (outbox insertion, serialization, etc.)
            // The PENDING row is already committed.
            // The outbox poller will retry any partially-written rows.
            // Client should poll GET /payments/{id} for resolution.
            log.error("Finalization failed for payment id={}, returning PENDING. " +
                    "OutboxPoller will retry.", saved.id(), e);
            return saved;
        }
    }

    public Optional<PaymentOrder> findPaymentByIdForMerchant(UUID id, UUID merchantId) {
        Optional<PaymentOrder> orderOptional = paymentRepository.findById(id);
        if (orderOptional.isPresent() && orderOptional.get().merchantId().equals(merchantId)) {
            return orderOptional;
        }
        return Optional.empty();
    }

    private PaymentOrder findExistingOrder(PaymentOrder order) {
        return paymentRepository.findByIdempotencyKey(order.idempotencyKey())
                .orElseThrow(() -> new DataIntegrityViolationException(
                        "Duplicate key detected but existing record could not be retrieved"));
    }

    private PaymentOrder saveNewOrder(PaymentOrder order) {
        PaymentOrder newPaymentOrder = paymentRepository.saveAndFlush(order);
        return newPaymentOrder;
    }
}
