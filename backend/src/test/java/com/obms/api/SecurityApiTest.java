package com.obms.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.obms.support.ApiTestTokens;
import com.obms.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API tests for the authorisation boundaries.
 *
 * <p>This is the suite that proves the access control is real rather than
 * cosmetic: unauthenticated calls are refused, a forged token is refused, a
 * customer cannot reach staff or admin endpoints, and one customer cannot read
 * another's records even with a perfectly valid token of their own.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestDataFactory.class, ApiTestTokens.class})
@DisplayName("API: authorisation boundaries")
class SecurityApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestDataFactory factory;
    @Autowired
    private ApiTestTokens tokens;

    @ParameterizedTest(name = "GET {0} is refused without a token")
    @ValueSource(strings = {
            "/api/dashboard",
            "/api/accounts",
            "/api/beneficiaries",
            "/api/notifications",
            "/api/analytics/spending",
            "/api/health-score",
            "/api/savings-goals",
            "/api/fraud-alerts",
            "/api/staff/stats",
            "/api/admin/audit"
    })
    @DisplayName("protected endpoints return 401 with no token")
    void protectedEndpointsRequireAuthentication(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("a forged token is rejected and grants nothing")
    void forgedTokenRejected() throws Exception {
        // The payload claims the ADMIN role, but the signature does not verify.
        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", "Bearer " + tokens.forgedToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a malformed Authorization header is ignored rather than trusted")
    void malformedHeaderRejected() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Basic abcdef"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a customer cannot reach the admin audit log")
    void customerCannotReachAdmin() throws Exception {
        var customer = factory.customerWithAccount(new BigDecimal("5000.00"));

        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(customer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("a customer cannot reach the staff KYC queue")
    void customerCannotReachStaff() throws Exception {
        var customer = factory.customerWithAccount(new BigDecimal("5000.00"));

        mockMvc.perform(get("/api/staff/kyc")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(customer)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a customer cannot reach the fraud investigation queue")
    void customerCannotReachInvestigation() throws Exception {
        var customer = factory.customerWithAccount(new BigDecimal("5000.00"));

        mockMvc.perform(get("/api/fraud/investigation/queue")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(customer)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a fraud analyst can reach the investigation queue")
    void analystCanReachInvestigation() throws Exception {
        var analyst = factory.customerWithAccount(new BigDecimal("1000.00"),
                BigDecimal.ZERO, "FRAUD_ANALYST");

        mockMvc.perform(get("/api/fraud/investigation/queue")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(analyst)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a fraud analyst still cannot reach the admin audit log")
    void analystCannotReachAdminAudit() throws Exception {
        var analyst = factory.customerWithAccount(new BigDecimal("1000.00"),
                BigDecimal.ZERO, "FRAUD_ANALYST");

        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(analyst)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("branch staff can reach the KYC queue")
    void staffCanReachKycQueue() throws Exception {
        var staff = factory.customerWithAccount(new BigDecimal("1000.00"),
                BigDecimal.ZERO, "BANK_STAFF");

        mockMvc.perform(get("/api/staff/kyc")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(staff)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an admin can reach the audit log")
    void adminCanReachAudit() throws Exception {
        var admin = factory.customerWithAccount(new BigDecimal("1000.00"),
                BigDecimal.ZERO, "ADMIN");

        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("one customer cannot read another customer's account")
    void cannotReadAnotherCustomersAccount() throws Exception {
        var attacker = factory.customerWithAccount(new BigDecimal("100.00"));
        var victim = factory.customerWithAccount(new BigDecimal("900000.00"));

        mockMvc.perform(get("/api/accounts/" + victim.account().getId())
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(attacker)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_OWNED"));
    }

    @Test
    @DisplayName("one customer cannot read another customer's transaction history")
    void cannotReadAnotherCustomersHistory() throws Exception {
        var attacker = factory.customerWithAccount(new BigDecimal("100.00"));
        var victim = factory.customerWithAccount(new BigDecimal("900000.00"));

        mockMvc.perform(get("/api/accounts/" + victim.account().getId() + "/transactions")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(attacker)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("one customer cannot generate another customer's statement")
    void cannotGenerateAnotherCustomersStatement() throws Exception {
        var attacker = factory.customerWithAccount(new BigDecimal("100.00"));
        var victim = factory.customerWithAccount(new BigDecimal("900000.00"));

        mockMvc.perform(get("/api/statements/" + victim.account().getId())
                        .param("from", "2026-01-01")
                        .param("to", "2026-12-31")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(attacker)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a customer cannot transfer out of another customer's account")
    void cannotTransferFromAnotherCustomersAccount() throws Exception {
        var attacker = factory.customerWithAccount(new BigDecimal("100.00"));
        var victim = factory.customerWithAccount(new BigDecimal("900000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("0.00"));
        var payee = factory.verifiedBeneficiary(attacker.customer(), receiver.account(), 90);

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(attacker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceAccountId", victim.account().getId(),
                                "beneficiaryId", payee.getId(),
                                "amount", new BigDecimal("1000.00"),
                                "description", "IDOR attempt"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the API error body never carries a stack trace")
    void errorsDoNotLeakInternals() throws Exception {
        mockMvc.perform(get("/api/accounts/999999999")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(
                                factory.customerWithAccount(new BigDecimal("100.00")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @DisplayName("public documentation endpoints stay reachable")
    void swaggerIsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
