package com.example.bank.dto;

import com.example.bank.domain.Account;
import com.example.bank.domain.EntryDirection;
import com.example.bank.domain.LedgerEntry;
import com.example.bank.domain.Money;
import com.example.bank.domain.Transaction;
import com.example.bank.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ExchangeResponse(
        Long transactionId,
        TransactionStatus status,
        String fromAccountNumber,
        String toAccountNumber,
        BigDecimal soldAmount,
        String soldCurrency,
        BigDecimal boughtAmount,
        String boughtCurrency,
        BigDecimal rate,
        String rateBaseCurrency,
        Instant createdAt) {

    public static ExchangeResponse from(Transaction tx, List<LedgerEntry> entries) {
        return new ExchangeResponse(tx.getId(), tx.getStatus(),
                customerAccount(entries, EntryDirection.DEBIT), customerAccount(entries, EntryDirection.CREDIT),
                Money.forDisplay(tx.getAmount(), tx.getCurrency()), tx.getCurrency(),
                Money.forDisplay(tx.getCounterAmount(), tx.getCounterCurrency()), tx.getCounterCurrency(),
                tx.getExchangeRate().stripTrailingZeros(), tx.getExchangeRateBase(), tx.getCreatedAt());
    }

    /** The customer's debited (sold) or credited (bought) account; the bank's FX accounts are skipped. */
    private static String customerAccount(List<LedgerEntry> entries, EntryDirection direction) {
        return entries.stream()
                .filter(e -> e.getDirection() == direction)
                .map(LedgerEntry::getAccount)
                .filter(Account::isCustomerAccount)
                .map(Account::getAccountNumber)
                .findFirst()
                .orElse(null);
    }
}
