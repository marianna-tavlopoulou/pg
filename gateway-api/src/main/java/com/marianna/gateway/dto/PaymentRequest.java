package com.marianna.gateway.dto;

import com.marianna.gateway.domain.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PaymentRequest(
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotBlank @Size(min = 3, max = 3, message = "Currency must be ISO 4217 e.g. EUR") String currency,
    @NotNull PaymentMethod method,
    @NotBlank String description
) {}
