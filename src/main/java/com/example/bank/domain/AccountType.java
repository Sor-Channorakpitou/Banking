package com.example.bank.domain;

public enum AccountType {
    /** Owned by a user; what the API calls an "account". */
    CUSTOMER,
    /**
     * The bank's own cash for one currency: the other side of every deposit and
     * withdrawal. Its balance is the negative of all cash customers have paid
     * in, so the sum over every account in a currency is always zero.
     */
    CASH,
    /**
     * The bank's foreign-exchange position in one currency: the other side of each
     * leg of a currency exchange.
     */
    FX
}
