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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
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

    public Account(String accountNumber, User owner, String currency) {
        this.accountNumber = accountNumber;
        this.owner = owner;
        this.currency = currency;
        this.status = AccountStatus.ACTIVE;
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
