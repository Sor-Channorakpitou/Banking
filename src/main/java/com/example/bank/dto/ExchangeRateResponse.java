package com.example.bank.dto;

import com.example.bank.domain.ExchangeRate;

import java.math.BigDecimal;
import java.time.Instant;

/** "1 USD: we buy at 4,090 KHR, we sell at 4,110 KHR". */
public record ExchangeRateResponse(String baseCurrency, String quoteCurrency, BigDecimal buyRate,
                                   BigDecimal sellRate, Instant updatedAt) {

    public static ExchangeRateResponse from(ExchangeRate rate) {
        return new ExchangeRateResponse(rate.getBaseCurrency(), rate.getQuoteCurrency(),
                rate.getBuyRate().stripTrailingZeros(), rate.getSellRate().stripTrailingZeros(), rate.getCreatedAt());
    }
}
