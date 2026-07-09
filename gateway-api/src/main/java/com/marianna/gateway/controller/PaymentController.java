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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
        @Operation(summary = "Submit a payment — include Idempotency-Key header to safely retry", description = """
                        Creates a new payment order. The request must include an Idempotency-Key header to safely retry the request in case of network failures or timeouts.
                        If the same Idempotency-Key is used for a subsequent request, the server will return the same result as the original request, preventing duplicate payments.
                        The response will indicate the status of the payment order.
                        If the payment is declined, a 422 Unprocessable Entity status will be returned. If the payment is pending or completed,
                        else a 201 Created status will be returned.
                        A response with HTTP 201 Created does not necessarily mean the payment has completed processing.
                        If the returned payment status is PENDING, finalization will continue asynchronously.
                        Clients should poll GET /api/v1/payments/{id} until the payment reaches a terminal state (COMPLETED or DECLINED).
                        """)
        @ApiResponses({
                        @ApiResponse(responseCode = "201", description = "Payment order created successfully (status may be PENDING or COMPLETED)"),
                        @ApiResponse(responseCode = "422", description = "Payment order was declined"),
                        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
                        @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid authentication token")
        })
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
                                                .body(PaymentResponse.from(submittedOrder)); // Even if the transaction
                                                                                             // is pending return 201
        }

        @GetMapping("/{id}")
        @Operation(summary = "Get payment status")
        public PaymentResponse getPayment(@PathVariable UUID id, @AuthenticationPrincipal UUID merchantId) {
                return paymentService.findPaymentByIdForMerchant(id, merchantId).map(PaymentResponse::from)
                                .orElseThrow(() -> new PaymentNotFoundException(id));
        }
}
