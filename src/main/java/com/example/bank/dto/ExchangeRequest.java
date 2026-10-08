package com.example.bank.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Exchange between two of the caller's own accounts; amount is in the source account's currency. */
public record ExchangeRequest(
        @NotNull Long fromAccountId,
        @NotNull Long toAccountId,
        @NotNull BigDecimal amount) {
}
