package com.example.bank.dto;

/**
 * Who an account number belongs to, shown before sending money so the sender can
 * catch a typo. The name is partly hidden ("DARA C."): enough to recognise someone
 * you know, not enough to learn a stranger's full name.
 */
public record RecipientResponse(String accountNumber, String currency, String holderName) {
}
