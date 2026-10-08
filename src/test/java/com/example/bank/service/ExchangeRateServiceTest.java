package com.example.bank.service;

import com.example.bank.config.AccountProperties;
import com.example.bank.domain.ExchangeRate;
import com.example.bank.exception.BankException;
import com.example.bank.exception.ErrorCode;
import com.example.bank.repository.ExchangeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExchangeRateServiceTest {

    @Mock
    private ExchangeRateRepository repository;
    @Mock
    private AuditService auditService;

    private ExchangeRateService service;

    @BeforeEach
    void setUp() {
        service = new ExchangeRateService(repository, new AccountProperties(Set.of("USD", "KHR", "EUR")),
                auditService);
        when(repository.findFirstByBaseCurrencyAndQuoteCurrencyOrderByIdDesc("USD", "KHR"))
                .thenReturn(Optional.of(new ExchangeRate("USD", "KHR", new BigDecimal("4090"),
                        new BigDecimal("4110"), null)));
    }

    @Test
    void sellingDollarsUsesTheBuyRate() {
        ExchangeRateService.Quote q = service.convert("USD", "KHR", new BigDecimal("100"));

        assertThat(q.convertedAmount()).isEqualByComparingTo("409000");
        assertThat(q.rate()).isEqualByComparingTo("4090");
        assertThat(q.rateBase()).isEqualTo("USD");
    }

    @Test
    void buyingDollarsUsesTheSellRateAndRoundsDown() {
        assertThat(service.convert("KHR", "USD", new BigDecimal("4110")).convertedAmount())
                .isEqualByComparingTo("1.00");
        // 4109 / 4110 = 0.99975...: the customer gets 0.99, never a rounded-up 1.00.
        assertThat(service.convert("KHR", "USD", new BigDecimal("4109")).convertedAmount())
                .isEqualByComparingTo("0.99");
    }

    @Test
    void sameCurrencyAndMissingRateAreRejected() {
        assertThatThrownBy(() -> service.convert("USD", "USD", BigDecimal.ONE))
                .isInstanceOf(BankException.class).extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> service.convert("EUR", "KHR", BigDecimal.ONE))
                .isInstanceOf(BankException.class).extracting("errorCode")
                .isEqualTo(ErrorCode.EXCHANGE_RATE_UNAVAILABLE);
    }
}
