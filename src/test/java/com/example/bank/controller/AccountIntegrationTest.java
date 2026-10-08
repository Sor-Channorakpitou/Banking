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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(mockMvc, objectMapper);
    }

    @Test
    void customerOpensAndListsOwnAccounts() throws Exception {
        String token = api.newCustomer();

        api.perform(post("/api/accounts"), token, Map.of("currency", "usd"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.accountNumber").value(matchesPattern("\\d{11}")));
        api.openAccount(token, "KHR");

        api.perform(get("/api/accounts"), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void unsupportedCurrencyIsRejected() throws Exception {
        String token = api.newCustomer();

        api.perform(post("/api/accounts"), token, Map.of("currency", "XYZ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
    }

    @Test
    void customerCannotSeeSomeoneElsesAccount() throws Exception {
        String alice = api.newCustomer();
        String bob = api.newCustomer();
        long aliceAccount = api.openAccount(alice, "USD");

        api.perform(get("/api/accounts/" + aliceAccount), bob, null)
                .andExpect(status().isNotFound());
        api.perform(get("/api/accounts/" + aliceAccount), api.adminToken(), null)
                .andExpect(status().isOk());
    }

    @Test
    void adminFreezesUnfreezesAndClosesAccount() throws Exception {
        String customer = api.newCustomer();
        String admin = api.adminToken();
        long accountId = api.openAccount(customer, "USD");

        api.perform(post("/api/admin/accounts/" + accountId + "/freeze"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));
        api.perform(post("/api/admin/accounts/" + accountId + "/freeze"), admin, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_STATE_CONFLICT"));
        api.perform(post("/api/admin/accounts/" + accountId + "/unfreeze"), admin, null)
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        api.perform(post("/api/admin/accounts/" + accountId + "/close"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        api.perform(post("/api/admin/accounts/" + accountId + "/unfreeze"), admin, null)
                .andExpect(status().isConflict());
    }

    @Test
    void customersCannotUseAdminAccountEndpoints() throws Exception {
        String customer = api.newCustomer();
        long accountId = api.openAccount(customer, "USD");

        api.perform(post("/api/admin/accounts/" + accountId + "/freeze"), customer, null)
                .andExpect(status().isForbidden());
    }

    @Test
    void adminListsAllAccountsPaged() throws Exception {
        String customer = api.newCustomer();
        api.openAccount(customer, "USD");

        api.perform(get("/api/admin/accounts?page=0&size=1"), api.adminToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.size").value(1));
        api.perform(get("/api/admin/accounts?size=500"), api.adminToken(), null)
                .andExpect(status().isBadRequest());
    }
}
