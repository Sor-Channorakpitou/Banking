package com.example.bank.dto;

/**
 * A saved payee. {@code currency} and {@code holderName} are null when the account
 * no longer accepts money (closed or frozen); {@code available} says so directly.
 */
public record PayeeResponse(Long id, String nickname, String accountNumber, String currency, String holderName,
                            boolean available) {
}
