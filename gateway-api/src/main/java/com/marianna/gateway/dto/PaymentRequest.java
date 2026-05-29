package com.marianna.gateway.dto;

import com.marianna.gateway.domain.Currency;
import com.marianna.gateway.domain.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaymentRequest(
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotNull Currency currency,
    @NotNull PaymentMethod method,
    @NotBlank String description
) {}
