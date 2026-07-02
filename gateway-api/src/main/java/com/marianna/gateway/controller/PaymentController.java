package com.marianna.gateway.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.marianna.gateway.domain.PaymentOrder;
import com.marianna.gateway.domain.PaymentStatus;
import com.marianna.gateway.dto.PaymentRequest;
import com.marianna.gateway.dto.PaymentResponse;
import com.marianna.gateway.exception.PaymentNotFoundException;
import com.marianna.gateway.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment processing endpoints")
@SecurityRequirement(name = "Bearer")
public class PaymentController {

        private final PaymentService paymentService;

        public PaymentController(PaymentService paymentService) {
                this.paymentService = paymentService;
        }

        @PostMapping
        @Operation(summary = "Submit a payment — include Idempotency-Key header to safely retry")
        public ResponseEntity<PaymentResponse> submitPayment(
                        @RequestHeader("Idempotency-Key") String idempotencyKey,
                        @AuthenticationPrincipal UUID merchantId,
                        @Valid @RequestBody PaymentRequest request) {
                PaymentOrder order = PaymentOrder.create(request.customerId(), merchantId, request.amount(),
                                request.currency(), request.method(), idempotencyKey, request.description());
                PaymentOrder submittedOrder = paymentService.submit(order);
                return submittedOrder.status().equals(PaymentStatus.DECLINED)
                                ? ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                                .body(PaymentResponse.from(submittedOrder))
                                : ResponseEntity.status(HttpStatus.CREATED)
                                                .body(PaymentResponse.from(submittedOrder));
        }

        @GetMapping("/{id}")
        @Operation(summary = "Get payment status")
        public PaymentResponse getPayment(@PathVariable UUID id, @AuthenticationPrincipal UUID merchantId) {
                return paymentService.findPaymentByIdForMerchant(id, merchantId).map(PaymentResponse::from)
                                .orElseThrow(() -> new PaymentNotFoundException(id));
        }
}
