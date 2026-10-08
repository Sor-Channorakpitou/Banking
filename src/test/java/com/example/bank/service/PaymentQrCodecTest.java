package com.example.bank.service;

import com.example.bank.exception.BankException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentQrCodecTest {

    @Test
    void crcMatchesTheStandardCheckValue() {
        // CRC-16/CCITT-FALSE of "123456789" is 29B1 by definition of the algorithm.
        assertThat(PaymentQrCodec.crc16("123456789")).isEqualTo("29B1");
    }

    @Test
    void roundTripWithAndWithoutAmount() {
        var withAmount = new PaymentQrCodec.Payment("10000000017", "USD", new BigDecimal("30.25"), "DARA C.");
        String payload = PaymentQrCodec.encode(withAmount);

        assertThat(payload).startsWith("000201010212").contains("5303840").contains("540530.25");
        assertThat(PaymentQrCodec.decode(payload)).isEqualTo(withAmount);

        var open = new PaymentQrCodec.Payment("10000000025", "KHR", null, "SOPHEAK S.");
        String openPayload = PaymentQrCodec.encode(open);
        assertThat(openPayload).contains("010211").contains("5303116");
        assertThat(PaymentQrCodec.decode(openPayload).amount()).isNull();
    }

    @Test
    void tamperedCodeIsRejected() {
        String payload = PaymentQrCodec.encode(
                new PaymentQrCodec.Payment("10000000017", "USD", new BigDecimal("1.25"), "DARA C."));
        String tampered = payload.replace("54041.25", "54049.25"); // change the amount, keep the old CRC

        assertThatThrownBy(() -> PaymentQrCodec.decode(tampered))
                .isInstanceOf(BankException.class)
                .hasMessageContaining("checksum");
        assertThatThrownBy(() -> PaymentQrCodec.decode("hello")).isInstanceOf(BankException.class);
    }

    @Test
    void namesAreMasked() {
        assertThat(RecipientService.maskName("Dara Chan")).isEqualTo("DARA C.");
        assertThat(RecipientService.maskName("  sok  dara chan ")).isEqualTo("SOK D. C.");
        assertThat(RecipientService.maskName("Vanna")).isEqualTo("V***");
    }
}
