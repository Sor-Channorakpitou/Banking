package com.example.bank.service;

import com.example.bank.dto.TransactionResponse;

/**
 * @param replayed true when an earlier request with the same Idempotency-Key
 *                 already did the work and this is its stored result
 */
public record MoneyMovementResult(TransactionResponse transaction, boolean replayed) {
}
