package com.example.bank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Source is one of the caller's own accounts (by ID). Destination is identified
 * by account number, which is what a person shares to receive money; internal
 * IDs stay private.
 */
public record TransferRequest(
        @NotNull Long fromAccountId,
        @NotBlank String toAccountNumber,
        @NotNull BigDecimal amount,
        @Size(max = 255) String description) {
}
