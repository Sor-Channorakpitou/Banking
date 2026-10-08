package com.example.bank.controller;

import com.example.bank.support.TestApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uses the starting rate from application.yml: USD/KHR buy 4,090, sell 4,110. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExchangeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private TestApi api;
    private String customer;
    private long usd;
    private long khr;

    @BeforeEach
    void setUp() throws Exception {
        api = new TestApi(mockMvc, objectMapper);
        customer = api.newCustomer();
        usd = api.openAccount(customer, "USD");
        khr = api.openAccount(customer, "KHR");
        api.deposit(customer, usd, "100.00", TestApi.newKey()).andExpect(status().isCreated());
    }

    @Test
    void dollarsToRielAndBackUseBuyAndSellRates() throws Exception {
        exchange(usd, khr, "10.00", TestApi.newKey())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.soldCurrency").value("USD"))
                .andExpect(jsonPath("$.boughtAmount").value(40900.00))
                .andExpect(jsonPath("$.rate").value(4090));
        assertThat(api.balance(customer, usd)).isEqualByComparingTo("90.00");
        assertThat(api.balance(customer, khr)).isEqualByComparingTo("40900");

        // Back the other way the bank sells dollars at 4,110: 32,880 KHR buys exactly 8.00 USD.
        exchange(khr, usd, "32880", TestApi.newKey())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.boughtAmount").value(8.00))
                .andExpect(jsonPath("$.rate").value(4110));
        assertThat(api.balance(customer, khr)).isEqualByComparingTo("8020");
        assertThat(api.balance(customer, usd)).isEqualByComparingTo("98.00");
    }

    @Test
    void quoteMatchesTheExchange() throws Exception {
        api.perform(get("/api/exchange-rates/quote?from=USD&to=KHR&amount=25"), customer, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convertedAmount").value(102250.00));
        api.perform(get("/api/exchange-rates"), customer, null)
                .andExpect(jsonPath("$[?(@.baseCurrency=='USD' && @.quoteCurrency=='KHR')].buyRate").exists());
    }

    @Test
    void repeatedKeyExchangesOnlyOnce() throws Exception {
        String key = TestApi.newKey();
        exchange(usd, khr, "5", key).andExpect(status().isCreated());
        exchange(usd, khr, "5.00", key)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"));

        assertThat(api.balance(customer, usd)).isEqualByComparingTo("95.00");
    }

    @Test
    void rejectsSameCurrencyOtherPeoplesAccountsAndMissingFunds() throws Exception {
        long secondUsd = api.openAccount(customer, "USD");
        exchange(usd, secondUsd, "1", TestApi.newKey())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String other = api.newCustomer();
        long othersKhr = api.openAccount(other, "KHR");
        exchange(usd, othersKhr, "1", TestApi.newKey()).andExpect(status().isNotFound());

        exchange(usd, khr, "100.01", TestApi.newKey())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void onlyAdminsPublishRates() throws Exception {
        Map<String, Object> rate = Map.of("baseCurrency", "USD", "quoteCurrency", "KHR",
                "buyRate", new BigDecimal("4080"), "sellRate", new BigDecimal("4100"));
        api.perform(post("/api/admin/exchange-rates"), customer, rate).andExpect(status().isForbidden());
        api.perform(post("/api/admin/exchange-rates"), api.adminToken(),
                        Map.of("baseCurrency", "USD", "quoteCurrency", "KHR",
                                "buyRate", new BigDecimal("4100"), "sellRate", new BigDecimal("4080")))
                .andExpect(status().isBadRequest());
    }

    private ResultActions exchange(long from, long to, String amount, String key) throws Exception {
        return api.perform(post("/api/exchanges").header("Idempotency-Key", key), customer,
                Map.of("fromAccountId", from, "toAccountId", to, "amount", new BigDecimal(amount)));
    }
}
