package com.example.bank.domain;

/**
 * Side of a ledger entry. For a customer account, CREDIT increases the balance
 * and DEBIT decreases it.
 */
public enum EntryDirection {
    DEBIT,
    CREDIT
}
