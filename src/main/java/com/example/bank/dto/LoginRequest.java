package com.example.bank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * @param totpCode the 6-digit code from the authenticator app; only needed when the
 *                 user turned on two-step login (the API answers TOTP_REQUIRED otherwise)
 */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password,
        @Pattern(regexp = "\\d{6}") String totpCode) {

    public LoginRequest(String email, String password) {
        this(email, password, null);
    }
}
