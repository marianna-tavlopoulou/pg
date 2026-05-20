package com.marianna.gateway.controller;

import com.marianna.gateway.domain.PaymentOrder;
import com.marianna.gateway.dto.PaymentRequest;
import com.marianna.gateway.dto.PaymentResponse;
import com.marianna.gateway.exception.PaymentNotFoundException;
import com.marianna.gateway.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment processing endpoints")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @Operation(summary = "Submit a payment — include Idempotency-Key header to safely retry")
    public ResponseEntity<PaymentResponse> submitPayment(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestHeader("X-Merchant-Id") UUID merchantId,
        @Valid @RequestBody PaymentRequest request
    ) {
        PaymentOrder order = PaymentOrder.create(merchantId, request.amount(),
            request.currency(), request.method(), idempotencyKey, request.description());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(PaymentResponse.from(paymentService.submit(order)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment status")
    public PaymentResponse getPayment(@PathVariable UUID id) {
        return paymentService.findById(id).map(PaymentResponse::from)
            .orElseThrow(() -> new PaymentNotFoundException(id));
    }
}
