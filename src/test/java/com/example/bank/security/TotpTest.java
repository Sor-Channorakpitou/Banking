package com.example.bank.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** RFC 6238 appendix B: secret "12345678901234567890", T = 59s gives 94287082 (8 digits). */
    @Test
    void matchesTheRfcTestVector() {
        String secret = Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
        assertThat(secret).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.codeAt(secret, 59 / 30)).isEqualTo("287082"); // last 6 digits
    }

    @Test
    void acceptsOneStepOfClockDriftButNotMore() {
        String secret = Totp.newSecret();
        Instant now = Instant.parse("2026-10-08T12:00:00Z");
        long step = now.getEpochSecond() / 30;

        assertThat(Totp.verify(secret, Totp.codeAt(secret, step), now)).hasValue(step);
        assertThat(Totp.verify(secret, Totp.codeAt(secret, step - 1), now)).hasValue(step - 1);
        assertThat(Totp.verify(secret, Totp.codeAt(secret, step + 2), now)).isEmpty();
        assertThat(Totp.verify(secret, "12345", now)).isEmpty();
    }

    @Test
    void base32RoundTrip() {
        byte[] data = {0, 1, 2, (byte) 250, 127, 33, 64, 99, 100, 5};
        assertThat(Totp.base32Decode(Totp.base32(data))).isEqualTo(data);
    }
}
