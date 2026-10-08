package com.example.bank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** The owner is always the caller (taken from the token), so it isn't a field here. */
public record OpenAccountRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3-letter ISO 4217 code")
        String currency) {
}
