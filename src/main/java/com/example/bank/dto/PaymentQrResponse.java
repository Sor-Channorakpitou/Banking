package com.example.bank.dto;

import java.math.BigDecimal;

/**
 * A payment QR code: {@code payload} is the text to draw as a QR image. {@code amount}
 * is null for an open code where the payer chooses the amount.
 */
public record PaymentQrResponse(String payload, String accountNumber, String currency, BigDecimal amount,
                                String holderName) {
}
