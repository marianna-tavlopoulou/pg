package com.marianna.gateway.domain;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum Currency {

    USD, EUR, GBP;

    private static final Map<String, Currency> LOOKUP = Arrays.stream(values())
            .collect(Collectors.toMap(
                    c -> c.name().toUpperCase(),
                    Function.identity()));

    public static Currency from(String value) {

        if (value == null) {
            throw new IllegalArgumentException("Currency is null");
        }

        Currency currency = LOOKUP.get(value.toUpperCase());

        if (currency == null) {
            throw new IllegalArgumentException(
                    "Invalid currency: " + value);
        }

        return currency;
    }
}