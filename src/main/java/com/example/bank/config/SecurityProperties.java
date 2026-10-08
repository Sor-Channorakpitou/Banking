package com.example.bank.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * {@code app.security.*}
 *
 * @param requireVerifiedEmail money can only leave an account once the owner's email is verified
 * @param dataKey              Base64 AES-256 key that encrypts secrets stored in the database (TOTP)
 * @param codeTtl              how long an emailed code stays valid
 * @param codeMaxAttempts      wrong guesses allowed per emailed code
 * @param corsAllowedOrigins   browser origins allowed to call the API (empty: same origin only)
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        boolean requireVerifiedEmail,
        @NotBlank String dataKey,
        @NotNull Duration codeTtl,
        @Min(1) int codeMaxAttempts,
        List<String> corsAllowedOrigins) {

    public SecurityProperties {
        corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
    }
}
