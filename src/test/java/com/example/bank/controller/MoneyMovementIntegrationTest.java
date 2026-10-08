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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Money movement end-to-end over HTTP: security, validation, service, ledger, H2. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MoneyMovementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private TestApi api;
    private String alice;
    private String bob;
    private long aliceAccount;
    private long bobAccount;
    private String bobAccountNumber;

    @BeforeEach
    void setUp() throws Exception {
        api = new TestApi(mockMvc, objectMapper);
        alice = api.newCustomer();
        bob = api.newCustomer();
        aliceAccount = api.openAccount(alice, "USD");
        bobAccount = api.openAccount(bob, "USD");
        bobAccountNumber = api.accountNumber(bob, bobAccount);
        api.deposit(alice, aliceAccount, "100.00", TestApi.newKey()).andExpect(status().isCreated());
    }

    @Test
    void successfulTransferMovesMoneyAndShowsInBothHistories() throws Exception {
        api.transfer(alice, aliceAccount, bobAccountNumber, "30.25", TestApi.newKey())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.toAccountNumber").value(bobAccountNumber));

        assertThat(api.balance(alice, aliceAccount)).isEqualByComparingTo("69.75");
        assertThat(api.balance(bob, bobAccount)).isEqualByComparingTo("30.25");

        api.perform(get("/api/accounts/" + bobAccount + "/transactions"), bob, null)
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER"))
                .andExpect(jsonPath("$.content[0].direction").value("CREDIT"));
        api.perform(get("/api/accounts/" + aliceAccount + "/transactions"), alice, null)
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].direction").value("DEBIT"));
    }

    @Test
    void insufficientFundsIsRejectedAndNothingChanges() throws Exception {
        api.transfer(alice, aliceAccount, bobAccountNumber, "100.01", TestApi.newKey())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.instance").value("/api/transfers"));

        assertThat(api.balance(alice, aliceAccount)).isEqualByComparingTo("100.00");
        assertThat(api.balance(bob, bobAccount)).isEqualByComparingTo("0");
        api.perform(get("/api/accounts/" + bobAccount + "/transactions"), bob, null)
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void repeatedIdempotencyKeyMovesMoneyOnlyOnce() throws Exception {
        String key = TestApi.newKey();
        long firstId = api.read(api.transfer(alice, aliceAccount, bobAccountNumber, "10", key)
                .andExpect(status().isCreated())).get("id").asLong();

        long secondId = api.read(api.transfer(alice, aliceAccount, bobAccountNumber, "10.00", key)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))).get("id").asLong();

        assertThat(secondId).isEqualTo(firstId);
        assertThat(api.balance(bob, bobAccount)).isEqualByComparingTo("10");

        api.transfer(alice, aliceAccount, bobAccountNumber, "20", key)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        api.perform(post("/api/accounts/" + aliceAccount + "/withdraw"), alice,
                        java.util.Map.of("amount", new BigDecimal("1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void frozenAccountCannotSendOrReceive() throws Exception {
        api.perform(post("/api/admin/accounts/" + bobAccount + "/freeze"), api.adminToken(), null)
                .andExpect(status().isOk());

        api.transfer(alice, aliceAccount, bobAccountNumber, "5", TestApi.newKey())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
    }

    @Test
    void moneyMovementsAreAudited() throws Exception {
        api.transfer(alice, aliceAccount, bobAccountNumber, "1", TestApi.newKey()).andExpect(status().isCreated());
        long aliceId = api.read(api.perform(get("/api/users/me"), alice, null)).get("id").asLong();

        api.perform(get("/api/admin/audit-logs?actorUserId=" + aliceId), api.adminToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].action", hasItem("TRANSFER")))
                .andExpect(jsonPath("$.content[*].action", hasItem("DEPOSIT")))
                .andExpect(jsonPath("$.content[*].action", hasItem("LOGIN_SUCCEEDED")));
    }
}
