package com.example.bank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A published rate for one currency pair, e.g. base USD / quote KHR, buy 4,090,
 * sell 4,110. Rows are never updated: a new rate is a new row, so the history of
 * every rate the bank ever offered is kept.
 */
@Entity
@Table(name = "exchange_rates")
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_currency", nullable = false, length = 3, updatable = false)
    private String baseCurrency;

    @Column(name = "quote_currency", nullable = false, length = 3, updatable = false)
    private String quoteCurrency;

    /** Quote units the bank pays for 1 base unit (the customer sells base). */
    @Column(name = "buy_rate", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal buyRate;

    /** Quote units the bank charges for 1 base unit (the customer buys base). */
    @Column(name = "sell_rate", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal sellRate;

    @Column(name = "created_by_id", updatable = false)
    private Long createdById;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExchangeRate() {
    }

    public ExchangeRate(String baseCurrency, String quoteCurrency, BigDecimal buyRate, BigDecimal sellRate,
                        Long createdById) {
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
        this.buyRate = buyRate;
        this.sellRate = sellRate;
        this.createdById = createdById;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    public BigDecimal getBuyRate() {
        return buyRate;
    }

    public BigDecimal getSellRate() {
        return sellRate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
