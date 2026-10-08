package com.example.bank.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of /api/auth/refresh and /api/auth/logout. */
public record RefreshRequest(@NotBlank String refreshToken) {
}
