package com.example.bank.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

/**
 * {@code app.accounts.*}: ISO 4217 codes the bank holds accounts in.
 */
@Validated
@ConfigurationProperties(prefix = "app.accounts")
public record AccountProperties(@NotEmpty Set<String> supportedCurrencies) {
}
