package com.example.bank.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body of a deposit or withdrawal. The amount's business rules (positive, right
 * number of decimals for the currency) are checked in the service, because they
 * depend on the account's currency.
 */
public record AmountRequest(
        @NotNull BigDecimal amount,
        @Size(max = 255) String description) {
}
