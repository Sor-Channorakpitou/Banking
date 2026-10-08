package com.example.bank.security;

import com.example.bank.service.MailService;
import com.example.bank.support.TestApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verification and limits switched on (the other tests run without them). Emails are
 * captured instead of sent, so the test can read the codes like a user would.
 */
@SpringBootTest(properties = {
        "app.security.require-verified-email=true",
        "app.limits.daily-outgoing.USD=100",
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountSecurityIntegrationTest {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MailService mailService;

    private TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(mockMvc, objectMapper);
    }

    @Test
    void moneyCanOnlyLeaveAfterEmailVerification() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = api.login(email, "password123");
        long usd = api.openAccount(token, "USD");
        api.deposit(token, usd, "50", TestApi.newKey()).andExpect(status().isCreated()); // money in: fine

        api.withdraw(token, usd, "1", TestApi.newKey())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        api.perform(post("/api/auth/verify-email"), null, Map.of("email", email, "code", "000000"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_CODE"));
        api.perform(post("/api/auth/verify-email"), null, Map.of("email", email, "code", lastCodeSentTo(email)))
                .andExpect(status().isNoContent());

        api.withdraw(token, usd, "1", TestApi.newKey()).andExpect(status().isCreated());
        api.perform(get("/api/users/me"), token, null).andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void dailyLimitStopsOutgoingMoney() throws Exception {
        String token = verifiedCustomer();
        long usd = api.openAccount(token, "USD");
        api.deposit(token, usd, "500", TestApi.newKey());

        api.withdraw(token, usd, "60", TestApi.newKey()).andExpect(status().isCreated());
        api.withdraw(token, usd, "50", TestApi.newKey())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_EXCEEDED"));
        api.perform(get("/api/accounts/" + usd + "/limits"), token, null)
                .andExpect(jsonPath("$.dailyLimit").value(100))
                .andExpect(jsonPath("$.remainingToday").value(40));
    }

    @Test
    void passwordResetSignsOutEverywhere() throws Exception {
        String email = uniqueEmail();
        register(email);
        JsonNode session = api.read(api.perform(post("/api/auth/login"), null,
                Map.of("email", email, "password", "password123")).andExpect(status().isOk()));

        api.perform(post("/api/auth/forgot-password"), null, Map.of("email", email)).andExpect(status().isAccepted());
        api.perform(post("/api/auth/forgot-password"), null, Map.of("email", "nobody-" + email))
                .andExpect(status().isAccepted()); // same answer: no way to probe which emails exist
        api.perform(post("/api/auth/reset-password"), null,
                        Map.of("email", email, "code", lastCodeSentTo(email), "newPassword", "new-password-1"))
                .andExpect(status().isNoContent());

        api.perform(post("/api/auth/refresh"), null, Map.of("refreshToken", session.get("refreshToken").asText()))
                .andExpect(status().isUnauthorized());
        api.login(email, "new-password-1");
    }

    @Test
    void twoStepLoginWithAnAuthenticatorApp() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = api.login(email, "password123");

        String secret = api.read(api.perform(post("/api/users/me/two-step/setup"), token, null)
                .andExpect(status().isOk())).get("secret").asText();
        long step = Instant.now().getEpochSecond() / 30;
        api.perform(post("/api/users/me/two-step/enable"), token, Map.of("code", Totp.codeAt(secret, step)))
                .andExpect(status().isNoContent());

        // Password alone is no longer enough.
        api.perform(post("/api/auth/login"), null, Map.of("email", email, "password", "password123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOTP_REQUIRED"));
        // The code used to enable it can't be replayed.
        api.perform(post("/api/auth/login"), null,
                        Map.of("email", email, "password", "password123", "totpCode", Totp.codeAt(secret, step)))
                .andExpect(jsonPath("$.code").value("INVALID_TOTP"));
        // The next window's code works (one step of clock drift is allowed).
        api.perform(post("/api/auth/login"), null,
                        Map.of("email", email, "password", "password123", "totpCode", Totp.codeAt(secret, step + 1)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ helpers

    private void register(String email) throws Exception {
        api.perform(post("/api/auth/register"), null,
                        Map.of("fullName", "Security Test", "email", email, "password", "password123"))
                .andExpect(status().isCreated());
    }

    private String verifiedCustomer() throws Exception {
        String email = uniqueEmail();
        register(email);
        api.perform(post("/api/auth/verify-email"), null, Map.of("email", email, "code", lastCodeSentTo(email)))
                .andExpect(status().isNoContent());
        return api.login(email, "password123");
    }

    /** Reads the 6-digit code from the newest email sent to this address. */
    private String lastCodeSentTo(String email) {
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(mailService, atLeastOnce()).send(eq(email), subject.capture(), anyString());
        Matcher m = CODE.matcher(subject.getValue());
        assertThat(m.find()).as("code in email subject").isTrue();
        return m.group(1);
    }

    private static String uniqueEmail() {
        return "sec-" + UUID.randomUUID() + "@example.com";
    }
}
