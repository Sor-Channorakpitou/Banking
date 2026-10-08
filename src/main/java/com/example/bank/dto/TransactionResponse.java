package com.example.bank.dto;

import com.example.bank.domain.Account;
import com.example.bank.domain.EntryDirection;
import com.example.bank.domain.LedgerEntry;
import com.example.bank.domain.Money;
import com.example.bank.domain.Transaction;
import com.example.bank.domain.TransactionStatus;
import com.example.bank.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A money movement as the client sees it. The from/to account numbers come from
 * the ledger entries (DEBIT = from, CREDIT = to). The bank's internal cash account
 * is shown as null, so a deposit has no "from" and a withdrawal has no "to".
 */
public record TransactionResponse(
        Long id,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        String currency,
        String description,
        String fromAccountNumber,
        String toAccountNumber,
        Instant createdAt) {

    public static TransactionResponse from(Transaction tx, List<LedgerEntry> entries) {
        return new TransactionResponse(tx.getId(), tx.getType(), tx.getStatus(),
                Money.forDisplay(tx.getAmount(), tx.getCurrency()), tx.getCurrency(), tx.getDescription(),
                customerAccountNumber(entries, EntryDirection.DEBIT),
                customerAccountNumber(entries, EntryDirection.CREDIT),
                tx.getCreatedAt());
    }

    private static String customerAccountNumber(List<LedgerEntry> entries, EntryDirection direction) {
        return entries.stream()
                .filter(e -> e.getDirection() == direction)
                .map(LedgerEntry::getAccount)
                .filter(Account::isCustomerAccount)
                .map(Account::getAccountNumber)
                .findFirst()
                .orElse(null);
    }
}
