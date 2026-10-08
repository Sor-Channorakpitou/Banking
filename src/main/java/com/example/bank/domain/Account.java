package com.example.bank.domain;

import com.example.bank.exception.BusinessRuleException;
import com.example.bank.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * A customer account. Note there is no balance field: the balance is always
 * derived from this account's {@link LedgerEntry} rows.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false, unique = true, length = 34)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AccountType type;

    /** Null for the bank's internal (CASH) accounts. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    /** ISO 4217 code, e.g. "USD". */
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    /**
     * Optimistic locking: Hibernate adds "WHERE version = ?" to every UPDATE and
     * bumps the value, so two concurrent edits of the same row can't both win.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
    }

    /** A customer account. */
    public Account(String accountNumber, User owner, String currency) {
        this(AccountType.CUSTOMER, accountNumber, owner, currency);
    }

    private Account(AccountType type, String accountNumber, User owner, String currency) {
        this.type = type;
        this.accountNumber = accountNumber;
        this.owner = owner;
        this.currency = currency;
        this.status = AccountStatus.ACTIVE;
    }

    /** The bank's cash account for one currency, e.g. "CASH-USD". */
    public static Account cash(String currency) {
        return internal(AccountType.CASH, currency);
    }

    /** An internal bank account, numbered by type and currency: "CASH-USD", "FX-KHR". */
    public static Account internal(AccountType type, String currency) {
        if (type == AccountType.CUSTOMER) {
            throw new IllegalArgumentException("Customer accounts need an owner");
        }
        return new Account(type, type.name() + "-" + currency, null, currency);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    // State changes go through methods that enforce the allowed transitions:
    //   ACTIVE <-> FROZEN, and ACTIVE/FROZEN -> CLOSED (final).

    public void freeze() {
        requireStatus(AccountStatus.ACTIVE, "freeze");
        status = AccountStatus.FROZEN;
    }

    public void unfreeze() {
        requireStatus(AccountStatus.FROZEN, "unfreeze");
        status = AccountStatus.ACTIVE;
    }

    /** Callers must check the balance is zero first (that needs the ledger). */
    public void close() {
        if (status == AccountStatus.CLOSED) {
            throw new BusinessRuleException(ErrorCode.ACCOUNT_STATE_CONFLICT,
                    "Account " + accountNumber + " is already closed");
        }
        status = AccountStatus.CLOSED;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isCustomerAccount() {
        return type == AccountType.CUSTOMER;
    }

    public boolean isOwnedBy(Long userId) {
        return owner != null && owner.getId().equals(userId);
    }

    private void requireStatus(AccountStatus required, String action) {
        if (status != required) {
            throw new BusinessRuleException(ErrorCode.ACCOUNT_STATE_CONFLICT,
                    "Cannot " + action + " account " + accountNumber + " in status " + status);
        }
    }

    public Long getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public AccountType getType() {
        return type;
    }

    public User getOwner() {
        return owner;
    }

    public String getCurrency() {
        return currency;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
