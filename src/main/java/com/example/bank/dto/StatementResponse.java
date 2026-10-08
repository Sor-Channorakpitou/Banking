package com.example.bank.dto;

import com.example.bank.domain.EntryDirection;
import com.example.bank.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

/**
 * A monthly account statement. It always reconciles:
 * openingBalance + totalCredits - totalDebits = closingBalance.
 * Each line shows the running balance after it.
 */
public record StatementResponse(
        String accountNumber,
        String currency,
        YearMonth month,
        Instant periodStart,
        Instant periodEnd,
        BigDecimal openingBalance,
        BigDecimal totalCredits,
        BigDecimal totalDebits,
        BigDecimal closingBalance,
        List<Line> lines) {

    public record Line(Instant date, Long transactionId, TransactionType type, String description,
                       EntryDirection direction, BigDecimal amount, BigDecimal balanceAfter) {
    }
}
