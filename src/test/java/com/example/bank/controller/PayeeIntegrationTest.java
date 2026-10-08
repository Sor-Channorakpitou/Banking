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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PayeeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private TestApi api;
    private String payer;
    private long payerUsd;
    private String receiver;
    private long receiverUsd;
    private String receiverNumber;

    @BeforeEach
    void setUp() throws Exception {
        api = new TestApi(mockMvc, objectMapper);
        payer = api.newCustomer();
        payerUsd = api.openAccount(payer, "USD");
        receiver = api.newCustomer(); // registered by TestApi as "Test User"
        receiverUsd = api.openAccount(receiver, "USD");
        receiverNumber = api.accountNumber(receiver, receiverUsd);
        api.deposit(payer, payerUsd, "50.00", TestApi.newKey());
    }

    @Test
    void lookupShowsAMaskedName() throws Exception {
        api.perform(get("/api/accounts/lookup?number=" + receiverNumber), payer, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holderName").value("TEST U."))
                .andExpect(jsonPath("$.currency").value("USD"));

        String bad = receiverNumber.substring(0, 10) + ((receiverNumber.charAt(10) - '0' + 1) % 10);
        api.perform(get("/api/accounts/lookup?number=" + bad), payer, null).andExpect(status().isBadRequest());
    }

    @Test
    void savePayeesButNotYourselfOrTwice() throws Exception {
        api.perform(post("/api/payees"), payer, Map.of("accountNumber", receiverNumber, "nickname", "Dara"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.holderName").value("TEST U."));
        api.perform(post("/api/payees"), payer, Map.of("accountNumber", receiverNumber, "nickname", "Again"))
                .andExpect(status().isConflict());
        String own = api.accountNumber(payer, payerUsd);
        api.perform(post("/api/payees"), payer, Map.of("accountNumber", own, "nickname", "Me"))
                .andExpect(status().isBadRequest());

        long id = api.read(api.perform(get("/api/payees"), payer, null)
                .andExpect(jsonPath("$", hasSize(1)))).get(0).get("id").asLong();
        api.perform(delete("/api/payees/" + id), receiver, null).andExpect(status().isNotFound()); // not theirs
        api.perform(delete("/api/payees/" + id), payer, null).andExpect(status().isNoContent());
        api.perform(get("/api/payees"), payer, null).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void qrCodeRoundTripThenPayIt() throws Exception {
        String payload = api.read(api.perform(get("/api/accounts/" + receiverUsd + "/payment-qr?amount=12.50"),
                        receiver, null).andExpect(status().isOk())).get("payload").asText();

        api.perform(post("/api/payment-qr/decode"), payer, Map.of("payload", payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(receiverNumber))
                .andExpect(jsonPath("$.amount").value(12.5))
                .andExpect(jsonPath("$.holderName").value("TEST U."));

        api.transfer(payer, payerUsd, receiverNumber, "12.50", TestApi.newKey()).andExpect(status().isCreated());
        assertThat(api.balance(receiver, receiverUsd)).isEqualByComparingTo("12.50");

        // Someone else's account: no QR for you.
        api.perform(get("/api/accounts/" + receiverUsd + "/payment-qr"), payer, null).andExpect(status().isNotFound());
    }
}
