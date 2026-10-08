package com.example.bank.domain;

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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A business event that moves money (deposit, withdrawal, transfer). The actual
 * movement is recorded as balanced {@link LedgerEntry} rows pointing back here.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    /** Client-supplied key; a retried request with the same key returns this transaction. */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(length = 255)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "initiated_by_id", nullable = false, updatable = false)
    private User initiatedBy;

    /** SHA-256 of the request, used to spot an idempotency key reused for a different request. */
    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
    private String requestFingerprint;

    // Second leg of an EXCHANGE (null for other types): what was credited, and the
    // published rate used, in quote units per 1 base unit (e.g. 4,090 KHR per USD).
    @Column(name = "counter_amount", precision = 19, scale = 4, updatable = false)
    private BigDecimal counterAmount;

    @Column(name = "counter_currency", length = 3, updatable = false)
    private String counterCurrency;

    @Column(name = "exchange_rate", precision = 19, scale = 6, updatable = false)
    private BigDecimal exchangeRate;

    @Column(name = "exchange_rate_base", length = 3, updatable = false)
    private String exchangeRateBase;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transaction() {
    }

    /** An EXCHANGE: {@code amount}/{@code currency} is what was sold, the counter leg what was bought. */
    public static Transaction exchange(BigDecimal soldAmount, String soldCurrency, BigDecimal boughtAmount,
                                       String boughtCurrency, BigDecimal rate, String rateBase,
                                       String idempotencyKey, User initiatedBy, String requestFingerprint) {
        Transaction tx = new Transaction(TransactionType.EXCHANGE, soldAmount, soldCurrency, idempotencyKey,
                soldCurrency + " to " + boughtCurrency, initiatedBy, requestFingerprint);
        tx.counterAmount = boughtAmount;
        tx.counterCurrency = boughtCurrency;
        tx.exchangeRate = rate;
        tx.exchangeRateBase = rateBase;
        return tx;
    }

    /**
     * Created as COMPLETED: the transaction row and its ledger entries are written
     * in one database transaction, so if this row is visible, the money has moved.
     * A failed attempt rolls back and leaves nothing behind.
     */
    public Transaction(TransactionType type, BigDecimal amount, String currency, String idempotencyKey,
                       String description, User initiatedBy, String requestFingerprint) {
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.description = description;
        this.initiatedBy = initiatedBy;
        this.requestFingerprint = requestFingerprint;
        this.status = TransactionStatus.COMPLETED;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getDescription() {
        return description;
    }

    public User getInitiatedBy() {
        return initiatedBy;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public BigDecimal getCounterAmount() {
        return counterAmount;
    }

    public String getCounterCurrency() {
        return counterCurrency;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public String getExchangeRateBase() {
        return exchangeRateBase;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
