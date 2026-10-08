package com.example.bank.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code app.exchange.*}: rates to publish at startup for pairs that have none yet,
 * so a fresh installation can exchange right away. Admins then publish new rates
 * through the API.
 */
@ConfigurationProperties(prefix = "app.exchange")
public record ExchangeProperties(List<InitialRate> initialRates) {

    public ExchangeProperties {
        initialRates = initialRates == null ? List.of() : List.copyOf(initialRates);
    }

    public record InitialRate(String base, String quote, BigDecimal buyRate, BigDecimal sellRate) {
    }
}
