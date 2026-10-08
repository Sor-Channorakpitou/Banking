package com.example.bank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DecodeQrRequest(@NotBlank @Size(max = 512) String payload) {
}
