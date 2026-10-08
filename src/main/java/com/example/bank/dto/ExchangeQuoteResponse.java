package com.example.bank.dto;

import java.math.BigDecimal;

/**
 * What an exchange would give right now, e.g. sell 100 USD, get 409,000 KHR at
 * 1 USD = 4,090 KHR. The rate can change before the exchange is made; the
 * exchange response shows the rate actually applied.
 */
public record ExchangeQuoteResponse(String fromCurrency, String toCurrency, BigDecimal amount,
                                    BigDecimal convertedAmount, BigDecimal rate, String rateBaseCurrency,
                                    String rateQuoteCurrency) {
}
