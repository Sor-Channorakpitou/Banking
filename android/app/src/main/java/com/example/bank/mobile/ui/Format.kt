package com.example.bank.mobile.ui

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val symbols = DecimalFormatSymbols(Locale.US)

fun currencySymbol(currency: String): String = when (currency) {
    "USD" -> "$"
    "KHR" -> "៛"
    "EUR" -> "€"
    else -> "$currency "
}

/** $4,280.50 · ៛1,250,000 (riel shows decimals only when there are any). */
fun formatMoney(amount: BigDecimal, currency: String): String {
    val abs = amount.abs()
    val wholeRiel = currency == "KHR" && abs.stripTrailingZeros().scale() <= 0
    val pattern = if (wholeRiel) "#,##0" else "#,##0.00"
    val sign = if (amount.signum() < 0) "−" else ""
    return sign + currencySymbol(currency) + DecimalFormat(pattern, symbols).format(abs)
}

fun formatSigned(amount: BigDecimal, currency: String, credit: Boolean): String =
    (if (credit) "+" else "−") + formatMoney(amount.abs(), currency)

/** Plain number for rates: 4,090 or 1.07. */
fun formatRate(rate: BigDecimal): String {
    val pattern = if (rate.stripTrailingZeros().scale() <= 0) "#,##0" else "#,##0.00##"
    return DecimalFormat(pattern, symbols).format(rate)
}

/** 10000000017 -> "1000 0000 017": easier to read aloud and to check. */
fun formatAccountNumber(number: String): String =
    if (number.length == 11) "${number.substring(0, 4)} ${number.substring(4, 8)} ${number.substring(8)}" else number

fun maskedAccountEnding(number: String): String = "•••" + number.takeLast(3)

private val zone: ZoneId get() = ZoneId.systemDefault()

fun localDate(isoInstant: String): LocalDate = Instant.parse(isoInstant).atZone(zone).toLocalDate()

fun time(isoInstant: String): String =
    Instant.parse(isoInstant).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))

fun shortDate(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern("d MMM", locale))

/** Parses what the user typed ("1,200.5") into an amount, or null if it isn't one. */
fun parseAmount(text: String): BigDecimal? =
    text.replace(",", "").trim().takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

/** Keeps only what can be part of an amount: digits and one decimal point, max 2 decimals. */
fun cleanAmountInput(input: String, maxDecimals: Int = 2): String {
    val filtered = input.filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    if (firstDot < 0) return filtered.take(13)
    val whole = filtered.substring(0, firstDot).take(13)
    val fraction = filtered.substring(firstDot + 1).replace(".", "").take(maxDecimals)
    return if (maxDecimals == 0) whole else "$whole.$fraction"
}

fun currencyDecimals(currency: String): Int = if (currency == "KHR") 0 else 2
