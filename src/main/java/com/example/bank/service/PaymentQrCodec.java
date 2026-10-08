package com.example.bank.service;

import com.example.bank.exception.BusinessRuleException;
import com.example.bank.exception.ErrorCode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Payment QR codes in the EMVCo "merchant-presented" layout, the international
 * format that Cambodia's KHQR is also built on. A payload is a chain of
 * Tag-Length-Value fields, e.g.
 * <pre>
 *   00 02 01            payload format version 01
 *   01 02 12            12 = amount included ("dynamic"), 11 = no amount ("static")
 *   29 31 0016com.example.bank0111 10000000017   our account info (nested TLV)
 *   53 03 840           currency, ISO 4217 numeric (840 USD, 116 KHR)
 *   54 05 30.25         amount (optional)
 *   58 02 KH            country
 *   59 07 DARA C.       name shown to the payer
 *   60 10 Phnom Penh    city
 *   63 04 A1B2          CRC-16 of everything before it, including "6304"
 * </pre>
 * The CRC makes a damaged or hand-edited code fail to decode. This is "KHQR-style",
 * not certified KHQR: other banks' apps won't pay into these codes.
 */
public final class PaymentQrCodec {

    static final String GUID = "com.example.bank";

    private PaymentQrCodec() {
    }

    public record Payment(String accountNumber, String currency, BigDecimal amount, String name) {
    }

    public static String encode(Payment p) {
        StringBuilder sb = new StringBuilder()
                .append(tlv("00", "01"))
                .append(tlv("01", p.amount() == null ? "11" : "12"))
                .append(tlv("29", tlv("00", GUID) + tlv("01", p.accountNumber())))
                .append(tlv("53", String.format("%03d", Currency.getInstance(p.currency()).getNumericCode())));
        if (p.amount() != null) {
            sb.append(tlv("54", p.amount().stripTrailingZeros().toPlainString()));
        }
        sb.append(tlv("58", "KH"))
                .append(tlv("59", ascii(p.name(), 25)))
                .append(tlv("60", "Phnom Penh"))
                .append("6304");
        return sb.append(crc16(sb.toString())).toString();
    }

    public static Payment decode(String payload) {
        if (payload == null || payload.length() < 8 || !payload.substring(payload.length() - 8).startsWith("6304")) {
            throw invalid("Not a payment QR code");
        }
        String body = payload.substring(0, payload.length() - 4);
        if (!crc16(body).equalsIgnoreCase(payload.substring(payload.length() - 4))) {
            throw invalid("The QR code is damaged or was changed (checksum mismatch)");
        }
        Map<String, String> fields = parse(body.substring(0, body.length() - 4));
        Map<String, String> account = parse(fields.getOrDefault("29", ""));
        if (!GUID.equals(account.get("00")) || account.get("01") == null) {
            throw invalid("This QR code is for another bank or app");
        }
        String currency = Currency.getAvailableCurrencies().stream()
                .filter(c -> String.format("%03d", c.getNumericCode()).equals(fields.get("53")))
                .map(Currency::getCurrencyCode)
                .findFirst()
                .orElseThrow(() -> invalid("Unknown currency in QR code"));
        BigDecimal amount;
        try {
            amount = fields.containsKey("54") ? new BigDecimal(fields.get("54")) : null;
        } catch (NumberFormatException e) {
            throw invalid("Invalid amount in QR code");
        }
        return new Payment(account.get("01"), currency, amount, fields.get("59"));
    }

    private static String tlv(String tag, String value) {
        if (value.length() > 99) {
            throw new IllegalArgumentException("TLV value too long for tag " + tag);
        }
        return tag + String.format("%02d", value.length()) + value;
    }

    private static Map<String, String> parse(String data) {
        Map<String, String> fields = new LinkedHashMap<>();
        int i = 0;
        try {
            while (i < data.length()) {
                String tag = data.substring(i, i + 2);
                int length = Integer.parseInt(data.substring(i + 2, i + 4));
                fields.put(tag, data.substring(i + 4, i + 4 + length));
                i += 4 + length;
            }
        } catch (RuntimeException e) {
            throw invalid("Malformed payment QR code");
        }
        return fields;
    }

    /** CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF), as EMV QR requires; 4 uppercase hex digits. */
    static String crc16(String data) {
        int crc = 0xFFFF;
        for (byte b : data.getBytes(StandardCharsets.UTF_8)) {
            crc ^= (b & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }

    /** EMV text fields are plain ASCII; Khmer names are replaced by a neutral placeholder. */
    private static String ascii(String s, int max) {
        String cleaned = s == null ? "" : s.replaceAll("[^\\x20-\\x7E]", "").trim();
        if (cleaned.isEmpty()) {
            cleaned = "ACCOUNT HOLDER";
        }
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }

    private static BusinessRuleException invalid(String message) {
        return new BusinessRuleException(ErrorCode.VALIDATION_FAILED, message);
    }
}
