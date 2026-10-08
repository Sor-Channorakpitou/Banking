package com.example.bank.dto;

import com.example.bank.domain.Account;
import com.example.bank.domain.AccountStatus;
import com.example.bank.domain.Money;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        String accountNumber,
        Long ownerId,
        String currency,
        AccountStatus status,
        BigDecimal balance,
        Instant createdAt) {

    public static AccountResponse from(Account account, BigDecimal balance) {
        Long ownerId = account.getOwner() == null ? null : account.getOwner().getId();
        return new AccountResponse(account.getId(), account.getAccountNumber(), ownerId,
                account.getCurrency(), account.getStatus(),
                Money.forDisplay(balance, account.getCurrency()), account.getCreatedAt());
    }
}
