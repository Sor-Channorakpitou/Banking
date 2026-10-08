package com.example.bank.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;

/**
 * {@code app.limits.daily-outgoing}: most money that can leave one account per
 * calendar day (UTC), per currency. A currency without an entry has no limit.
 */
@ConfigurationProperties(prefix = "app.limits")
public record LimitsProperties(Map<String, BigDecimal> dailyOutgoing) {

    public LimitsProperties {
        dailyOutgoing = dailyOutgoing == null ? Map.of() : Map.copyOf(dailyOutgoing);
    }
}
