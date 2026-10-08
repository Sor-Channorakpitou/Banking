package com.example.bank.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Note there is no "role" field: clients can't choose their role. Every
 * self-registered user is a CUSTOMER.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        // BCrypt only uses the first 72 bytes of a password, so longer ones are rejected.
        @NotBlank @Size(min = 8, max = 72) String password) {
}
