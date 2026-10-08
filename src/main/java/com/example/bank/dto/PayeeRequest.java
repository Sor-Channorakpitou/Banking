package com.example.bank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PayeeRequest(
        @NotBlank @Pattern(regexp = "\\d{11}", message = "must be an 11-digit account number") String accountNumber,
        @NotBlank @Size(max = 50) String nickname) {
}
