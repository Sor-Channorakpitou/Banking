package com.example.bank.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Small client for integration tests: wraps the HTTP calls most tests need so
 * each test reads as a scenario rather than as request-building code.
 */
public class TestApi {

    public static final String ADMIN_EMAIL = "admin@test.local";
    public static final String ADMIN_PASSWORD = "Admin123!";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public TestApi(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    /** Registers a fresh customer and returns their access token. */
    public String newCustomer() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Test User", "email", email, "password", "password123"))))
                .andExpect(status().isCreated());
        return login(email, "password123");
    }

    public String adminToken() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    public String login(String email, String password) throws Exception {
        JsonNode body = read(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()));
        return body.get("accessToken").asText();
    }

    public long openAccount(String token, String currency) throws Exception {
        JsonNode body = read(perform(post("/api/accounts"), token, Map.of("currency", currency))
                .andExpect(status().isCreated()));
        return body.get("id").asLong();
    }

    public String accountNumber(String token, long accountId) throws Exception {
        return read(perform(get("/api/accounts/" + accountId), token, null)
                .andExpect(status().isOk())).get("accountNumber").asText();
    }

    public BigDecimal balance(String token, long accountId) throws Exception {
        return read(perform(get("/api/accounts/" + accountId), token, null)
                .andExpect(status().isOk())).get("balance").decimalValue();
    }

    public ResultActions deposit(String token, long accountId, String amount, String idempotencyKey) throws Exception {
        return perform(post("/api/accounts/" + accountId + "/deposit")
                .header("Idempotency-Key", idempotencyKey), token, Map.of("amount", new BigDecimal(amount)));
    }

    public ResultActions withdraw(String token, long accountId, String amount, String idempotencyKey) throws Exception {
        return perform(post("/api/accounts/" + accountId + "/withdraw")
                .header("Idempotency-Key", idempotencyKey), token, Map.of("amount", new BigDecimal(amount)));
    }

    public ResultActions transfer(String token, long fromAccountId, String toAccountNumber, String amount,
                                  String idempotencyKey) throws Exception {
        return perform(post("/api/transfers").header("Idempotency-Key", idempotencyKey), token,
                Map.of("fromAccountId", fromAccountId, "toAccountNumber", toAccountNumber,
                        "amount", new BigDecimal(amount)));
    }

    /** Sends a request with a bearer token and, if given, a JSON body. */
    public ResultActions perform(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return mockMvc.perform(request);
    }

    public JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    public static String newKey() {
        return UUID.randomUUID().toString();
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
