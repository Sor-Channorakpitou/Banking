package com.example.bank.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record SetExchangeRateRequest(
        @NotNull @Pattern(regexp = "[A-Za-z]{3}") String baseCurrency,
        @NotNull @Pattern(regexp = "[A-Za-z]{3}") String quoteCurrency,
        @NotNull @Positive BigDecimal buyRate,
        @NotNull @Positive BigDecimal sellRate) {
}
