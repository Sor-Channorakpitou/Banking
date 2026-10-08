package com.example.bank.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Typed view of the {@code app.jwt.*} settings. {@code @Validated} makes startup
 * fail with a clear message if a value is missing, instead of failing on first login.
 *
 * @param secret         Base64-encoded HMAC key, at least 256 bits
 * @param issuer         value of the "iss" claim; tokens from other issuers are rejected
 * @param accessTokenTtl how long an access token stays valid
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl) {
}
