package com.example.bank.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional first admin account ({@code app.admin.*}). Leave the email empty to skip it.
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String email, String password, String fullName) {
}
