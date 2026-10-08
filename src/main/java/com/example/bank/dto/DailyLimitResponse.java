package com.example.bank.dto;

import java.math.BigDecimal;

/** {@code dailyLimit} and {@code remainingToday} are null when the currency has no limit. */
public record DailyLimitResponse(String currency, BigDecimal dailyLimit, BigDecimal usedToday,
                                 BigDecimal remainingToday) {
}
