package com.example.bank.dto;

import com.example.bank.domain.EntryDirection;
import com.example.bank.domain.LedgerEntry;
import com.example.bank.domain.Money;
import com.example.bank.domain.Transaction;
import com.example.bank.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One line of an account's history, seen from that account: CREDIT means money
 * came in, DEBIT means money went out.
 */
public record AccountTransactionResponse(
        Long transactionId,
        TransactionType type,
        EntryDirection direction,
        BigDecimal amount,
        String currency,
        String description,
        Instant createdAt) {

    public static AccountTransactionResponse from(LedgerEntry entry) {
        Transaction tx = entry.getTransaction();
        return new AccountTransactionResponse(tx.getId(), tx.getType(), entry.getDirection(),
                Money.forDisplay(entry.getAmount(), tx.getCurrency()), tx.getCurrency(),
                tx.getDescription(), entry.getCreatedAt());
    }
}
