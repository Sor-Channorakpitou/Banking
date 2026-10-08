package com.example.bank.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Money helpers. The database stores 4 decimal places, but each currency has its
 * own number of minor units (USD 2, KHR 2, JPY 0). Amounts are validated against
 * that on the way in, so formatting on the way out never has to round.
 */
public final class Money {

    private Money() {
    }

    public static int fractionDigits(String currency) {
        return Currency.getInstance(currency).getDefaultFractionDigits();
    }

    /** True if the amount has no more decimal places than the currency allows. */
    public static boolean hasValidScale(BigDecimal amount, String currency) {
        return amount.stripTrailingZeros().scale() <= fractionDigits(currency);
    }

    /** E.g. 12.5000 USD becomes 12.50. Throws if that would lose precision. */
    public static BigDecimal forDisplay(BigDecimal amount, String currency) {
        return amount.setScale(fractionDigits(currency), RoundingMode.UNNECESSARY);
    }
}
