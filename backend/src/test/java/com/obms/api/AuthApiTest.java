package com.obms.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.obms.support.ApiTestTokens;
import com.obms.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API tests for the authentication endpoints.
 *
 * <p>These exercise the real HTTP layer: routing, bean validation, the security
 * filter chain and the JSON contract. They are the "API" half of the test
 * matrix, as distinct from the unit tests on pure logic.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestDataFactory.class, ApiTestTokens.class})
@DisplayName("API: authentication")
class AuthApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestDataFactory factory;
    @Autowired
    private ApiTestTokens tokens;

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("POST /api/auth/register creates a customer and opens an account")
    void registerSucceeds() throws Exception {
        Map<String, Object> request = Map.of(
                "firstName", "Nisha",
                "lastName", "Verma",
                "email", "nisha.verma@test.obms",
                "mobile", "9812345678",
                "password", "StrongPass@2026",
                "city", "Bengaluru",
                "declaredMonthlyIncome", new BigDecimal("55000.00"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerNumber").exists())
                .andExpect(jsonPath("$.accountNumber").exists())
                .andExpect(jsonPath("$.username").value("nisha.verma@test.obms"));
    }

    @Test
    @DisplayName("POST /api/auth/register returns 400 with field errors for an invalid email")
    void registerRejectsInvalidEmail() throws Exception {
        Map<String, Object> request = Map.of(
                "firstName", "Bad",
                "lastName", "Email",
                "email", "not-an-email",
                "mobile", "9812345679",
                "password", "StrongPass@2026");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register rejects a weak password")
    void registerRejectsWeakPassword() throws Exception {
        Map<String, Object> request = Map.of(
                "firstName", "Weak",
                "lastName", "Password",
                "email", "weak.password@test.obms",
                "mobile", "9812345670",
                "password", "password");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register rejects a malformed mobile number")
    void registerRejectsBadMobile() throws Exception {
        Map<String, Object> request = Map.of(
                "firstName", "Bad",
                "lastName", "Mobile",
                "email", "bad.mobile@test.obms",
                "mobile", "12345",
                "password", "StrongPass@2026");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.mobile").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register returns 409 for a duplicate email")
    void registerRejectsDuplicateEmail() throws Exception {
        var existing = factory.customerWithAccount(new BigDecimal("1000.00"));

        Map<String, Object> request = Map.of(
                "firstName", "Duplicate",
                "lastName", "User",
                "email", existing.username(),
                "mobile", "9812345671",
                "password", "StrongPass@2026");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_IN_USE"));
    }

    @Test
    @DisplayName("POST /api/auth/login with valid credentials asks for a second factor")
    void loginRequiresSecondFactor() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("5000.00"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", user.username(),
                                "password", TestDataFactory.PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpRequired").value(true))
                .andExpect(jsonPath("$.preAuthToken").exists())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.maskedDestination").exists());
    }

    @Test
    @DisplayName("the login response never contains the one-time code")
    void loginResponseHidesTheCode() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("5000.00"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", user.username(),
                                "password", TestDataFactory.PASSWORD))))
                .andExpect(status().isOk())
                // The masked destination is present, the full address is not.
                .andExpect(content().string(not(containsString(user.username()))))
                .andExpect(jsonPath("$.otp").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/login returns 401 for a wrong password")
    void loginRejectsWrongPassword() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("5000.00"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", user.username(),
                                "password", "WrongPass@2026"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("an unknown username and a wrong password are indistinguishable")
    void noAccountEnumeration() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("5000.00"));

        String unknownBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", "nobody@test.obms",
                                "password", "WrongPass@2026"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String wrongPasswordBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", user.username(),
                                "password", "WrongPass@2026"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Both must report the same code and message, so the endpoint cannot
        // be used to discover which addresses hold accounts.
        org.assertj.core.api.Assertions.assertThat(
                        objectMapper.readTree(unknownBody).get("code").asText())
                .isEqualTo(objectMapper.readTree(wrongPasswordBody).get("code").asText());
        org.assertj.core.api.Assertions.assertThat(
                        objectMapper.readTree(unknownBody).get("message").asText())
                .isEqualTo(objectMapper.readTree(wrongPasswordBody).get("message").asText());
    }

    @Test
    @DisplayName("POST /api/auth/login returns 400 when fields are missing")
    void loginRequiresFields() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/auth/verify-otp returns 401 for a bogus pre-auth token")
    void verifyOtpRejectsBogusToken() throws Exception {
        mockMvc.perform(post("/api/auth/verify-otp")
                        .header("X-Pre-Auth-Token", "not.a.real.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "123456"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/verify-otp rejects a full access token used as a pre-auth token")
    void verifyOtpRejectsAccessToken() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("5000.00"));
        String accessToken = tokens.accessTokenFor(user);

        // An access token is not a pre-auth token. Accepting it here would let
        // a caller re-verify without the second factor.
        mockMvc.perform(post("/api/auth/verify-otp")
                        .header("X-Pre-Auth-Token", accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "123456"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/forgot-password returns 202 for an unknown address")
    void forgotPasswordDoesNotLeakExistence() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nobody@test.obms"))))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("GET /api/auth/me returns 401 without a token")
    void profileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/auth/me returns the profile for an authenticated customer")
    void profileReturnsCustomer() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("5000.00"));
        String token = tokens.accessTokenFor(user);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.username()))
                .andExpect(jsonPath("$.kycStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.customerNumber").exists());
    }
}
