package com.example.bank.controller;

import com.example.bank.support.TestApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 6 features: refresh tokens, login rate limiting, statements, metrics, request IDs. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability // Boot disables metrics exporters (e.g. Prometheus) in tests unless asked
@ActiveProfiles("test")
class ExtrasIntegrationTest {

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
    void refreshTokenRotatesAndReuseRevokesTheSession() throws Exception {
        String email = register();
        String first = loginBody(email, "password123").get("refreshToken").asText();

        JsonNode rotated = api.read(api.perform(post("/api/auth/refresh"), null, Map.of("refreshToken", first))
                .andExpect(status().isOk()));
        String second = rotated.get("refreshToken").asText();
        api.perform(get("/api/users/me"), rotated.get("accessToken").asText(), null).andExpect(status().isOk());

        // Replaying the consumed token looks like theft: rejected, and the whole family is revoked.
        api.perform(post("/api/auth/refresh"), null, Map.of("refreshToken", first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        api.perform(post("/api/auth/refresh"), null, Map.of("refreshToken", second))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        String email = register();
        String refresh = loginBody(email, "password123").get("refreshToken").asText();

        api.perform(post("/api/auth/logout"), null, Map.of("refreshToken", refresh))
                .andExpect(status().isNoContent());
        api.perform(post("/api/auth/refresh"), null, Map.of("refreshToken", refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedFailedLoginsAreRateLimited() throws Exception {
        String email = register();
        for (int i = 0; i < 5; i++) {
            api.perform(post("/api/auth/login"), null, Map.of("email", email, "password", "wrong-password"))
                    .andExpect(status().isUnauthorized());
        }
        // Even the right password is refused while the limit applies.
        api.perform(post("/api/auth/login"), null, Map.of("email", email, "password", "password123"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void monthlyStatementReconciles() throws Exception {
        String token = api.newCustomer();
        long account = api.openAccount(token, "USD");
        api.deposit(token, account, "100.00", TestApi.newKey()).andExpect(status().isCreated());
        api.withdraw(token, account, "30.00", TestApi.newKey()).andExpect(status().isCreated());
        YearMonth thisMonth = YearMonth.now(ZoneOffset.UTC);

        api.perform(get("/api/accounts/" + account + "/statements/" + thisMonth), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingBalance").value(0))
                .andExpect(jsonPath("$.totalCredits").value(100.00))
                .andExpect(jsonPath("$.totalDebits").value(30.00))
                .andExpect(jsonPath("$.closingBalance").value(70.00))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[1].balanceAfter").value(70.00));

        api.perform(get("/api/accounts/" + account + "/statements/" + thisMonth.plusMonths(1)), token, null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void metricsAreAdminOnlyAndIncludeBusinessCounters() throws Exception {
        String customer = api.newCustomer();
        long account = api.openAccount(customer, "USD");
        api.deposit(customer, account, "1", TestApi.newKey()).andExpect(status().isCreated());

        api.perform(get("/actuator/prometheus"), customer, null).andExpect(status().isForbidden());
        api.perform(get("/actuator/prometheus"), api.adminToken(), null)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("bank_money_movements_total")));
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void everyResponseCarriesARequestId() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-Request-Id", "trace-123"))
                .andExpect(header().string("X-Request-Id", "trace-123"));
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    private String register() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("fullName", "Test User", "email", email, "password", "password123"))))
                .andExpect(status().isCreated());
        return email;
    }

    private JsonNode loginBody(String email, String password) throws Exception {
        return api.read(api.perform(post("/api/auth/login"), null, Map.of("email", email, "password", password))
                .andExpect(status().isOk()));
    }
}
