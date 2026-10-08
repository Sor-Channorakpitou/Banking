package com.example.bank.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** {@code app.login-rate-limit.*}: see {@link com.example.bank.security.LoginRateLimiter}. */
@Validated
@ConfigurationProperties(prefix = "app.login-rate-limit")
public record LoginRateLimitProperties(
        @Min(1) int maxFailuresPerAccount,
        @Min(1) int maxFailuresPerIp,
        @NotNull Duration window) {
}
