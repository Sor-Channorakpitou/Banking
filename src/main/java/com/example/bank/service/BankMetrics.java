package com.example.bank.service;

import com.example.bank.domain.TransactionType;
import com.example.bank.exception.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Business metrics, on top of the technical ones Spring Boot already records
 * (HTTP latency per endpoint, JVM, DB pool...). In Prometheus format:
 * <pre>
 * bank_money_movements_total{type="TRANSFER",outcome="completed"}
 * bank_money_movement_amount_total{type="DEPOSIT",currency="USD"}
 * bank_business_errors_total{code="INSUFFICIENT_FUNDS"}
 * </pre>
 * Tags must have few possible values (type, currency, code). Never tag with user or
 * account IDs: each distinct tag value creates a new time series.
 */
@Component
public class BankMetrics {

    private final MeterRegistry registry;

    public BankMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void moneyMovementCompleted(TransactionType type, BigDecimal amount, String currency) {
        counter("bank.money.movements", "type", type.name(), "outcome", "completed").increment();
        // Counters are doubles: fine for dashboards, never for accounting.
        counter("bank.money.movement.amount", "type", type.name(), "currency", currency)
                .increment(amount.doubleValue());
    }

    public void moneyMovementReplayed(TransactionType type) {
        counter("bank.money.movements", "type", type.name(), "outcome", "replayed").increment();
    }

    public void businessError(ErrorCode code) {
        counter("bank.business.errors", "code", code.name()).increment();
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }
}
