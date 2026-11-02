package com.obms.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.obms.common.BankTime;
import com.obms.domain.Enums;
import com.obms.service.LedgerService;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API tests for the core banking surface: dashboard, accounts, transfers,
 * beneficiaries, bills, statements and the intelligence endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestDataFactory.class, ApiTestTokens.class})
@DisplayName("API: core banking endpoints")
class BankingApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestDataFactory factory;
    @Autowired
    private ApiTestTokens tokens;
    @Autowired
    private LedgerService ledgerService;

    @Test
    @DisplayName("GET /api/dashboard returns a payload built from the customer's own records")
    void dashboardReturnsRealFigures() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("37500.00"));
        String token = tokens.accessTokenFor(user);

        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(user.customer().fullName()))
                .andExpect(jsonPath("$.totalBalance").value(37500.00))
                .andExpect(jsonPath("$.accounts").isArray())
                .andExpect(jsonPath("$.accounts[0].accountNumber")
                        .value(user.account().getAccountNumber()))
                .andExpect(jsonPath("$.kycStatus").value("VERIFIED"));
    }

    @Test
    @DisplayName("GET /api/accounts lists only the caller's accounts")
    void accountsListIsScopedToCaller() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("1000.00"));
        factory.customerWithAccount(new BigDecimal("999999.00"));

        mockMvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountNumber")
                        .value(user.account().getAccountNumber()));
    }

    @Test
    @DisplayName("POST /api/transfers settles a small transfer and returns a reference")
    void transferApiSettles() throws Exception {
        var sender = factory.customerWithAccount(new BigDecimal("40000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("0.00"));
        var payee = factory.verifiedBeneficiary(sender.customer(), receiver.account(), 120);

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(sender))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceAccountId", sender.account().getId(),
                                "beneficiaryId", payee.getId(),
                                "amount", new BigDecimal("1200.00"),
                                "description", "API test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("COMPLETED"))
                .andExpect(jsonPath("$.reference").exists())
                .andExpect(jsonPath("$.balanceAfter").value(38800.00));
    }

    @Test
    @DisplayName("POST /api/transfers asks for verification above the threshold")
    void transferApiAsksForOtp() throws Exception {
        var sender = factory.customerWithAccount(new BigDecimal("90000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("0.00"));
        var payee = factory.verifiedBeneficiary(sender.customer(), receiver.account(), 120);

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceAccountId", sender.account().getId(),
                                "beneficiaryId", payee.getId(),
                                "amount", new BigDecimal("30000.00"),
                                "description", "Large"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("VERIFICATION_REQUIRED"))
                .andExpect(jsonPath("$.pendingRef").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/transfers returns 400 when required fields are missing")
    void transferApiValidatesBody() throws Exception {
        var sender = factory.customerWithAccount(new BigDecimal("40000.00"));

        Map<String, Object> incomplete = new HashMap<>();
        incomplete.put("description", "No amount, no source");

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(incomplete)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.amount").exists())
                .andExpect(jsonPath("$.fieldErrors.sourceAccountId").exists());
    }

    @Test
    @DisplayName("POST /api/transfers rejects a zero amount")
    void transferApiRejectsZeroAmount() throws Exception {
        var sender = factory.customerWithAccount(new BigDecimal("40000.00"));
        var receiver = factory.customerWithAccount(new BigDecimal("0.00"));
        var payee = factory.verifiedBeneficiary(sender.customer(), receiver.account(), 120);

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceAccountId", sender.account().getId(),
                                "beneficiaryId", payee.getId(),
                                "amount", BigDecimal.ZERO))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }

    @Test
    @DisplayName("POST /api/beneficiaries creates an unverified payee")
    void addBeneficiaryApi() throws Exception {
        var owner = factory.customerWithAccount(new BigDecimal("10000.00"));
        var other = factory.customerWithAccount(new BigDecimal("5000.00"));

        mockMvc.perform(post("/api/beneficiaries")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "beneficiaryName", other.customer().fullName(),
                                "accountNumber", other.account().getAccountNumber(),
                                "ifscCode", "OBMS0000001",
                                "nickname", "Test payee"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.payable").value(false))
                // The account number must come back masked, not in full.
                .andExpect(jsonPath("$.maskedAccountNumber").value(
                        "XXXX" + other.account().getAccountNumber()
                                .substring(other.account().getAccountNumber().length() - 4)));
    }

    @Test
    @DisplayName("POST /api/beneficiaries rejects a malformed IFSC code")
    void addBeneficiaryValidatesIfsc() throws Exception {
        var owner = factory.customerWithAccount(new BigDecimal("10000.00"));
        var other = factory.customerWithAccount(new BigDecimal("5000.00"));

        mockMvc.perform(post("/api/beneficiaries")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "beneficiaryName", "Someone",
                                "accountNumber", other.account().getAccountNumber(),
                                "ifscCode", "BADIFSC"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.ifscCode").exists());
    }

    @Test
    @DisplayName("POST /api/beneficiaries refuses the customer's own account")
    void cannotAddOwnAccountAsPayee() throws Exception {
        var owner = factory.customerWithAccount(new BigDecimal("10000.00"));

        mockMvc.perform(post("/api/beneficiaries")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "beneficiaryName", "Me",
                                "accountNumber", owner.account().getAccountNumber(),
                                "ifscCode", "OBMS0000001"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OWN_ACCOUNT"));
    }

    @Test
    @DisplayName("GET /api/bills/billers lists the seeded biller catalogue")
    void listsBillers() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("10000.00"));

        mockMvc.perform(get("/api/bills/billers")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].code").exists())
                .andExpect(jsonPath("$[0].identifierLabel").exists());
    }

    @Test
    @DisplayName("GET /api/statements returns a PDF with a download filename")
    void statementReturnsPdf() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("20000.00"),
                BigDecimal.ZERO, "CUSTOMER");

        ledgerService.postDebit(user.account().getId(), new BigDecimal("500.00"),
                Enums.TransactionType.WITHDRAWAL, "Swiggy order", "Swiggy",
                Enums.SpendCategory.FOOD);

        mockMvc.perform(get("/api/statements/" + user.account().getId())
                        .param("from", BankTime.today().minusMonths(1).toString())
                        .param("to", BankTime.today().toString())
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(".pdf")));
    }

    @Test
    @DisplayName("GET /api/statements rejects an inverted date range")
    void statementRejectsBadRange() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("1000.00"));

        mockMvc.perform(get("/api/statements/" + user.account().getId())
                        .param("from", "2026-06-01")
                        .param("to", "2026-05-01")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    @DisplayName("GET /api/analytics/spending returns a breakdown of real transactions")
    void analyticsReflectsLedger() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("50000.00"),
                BigDecimal.ZERO, "CUSTOMER");

        ledgerService.postDebit(user.account().getId(), new BigDecimal("1500.00"),
                Enums.TransactionType.WITHDRAWAL, "Swiggy order", "Swiggy",
                Enums.SpendCategory.FOOD);
        ledgerService.postDebit(user.account().getId(), new BigDecimal("2500.00"),
                Enums.TransactionType.WITHDRAWAL, "Amazon order", "Amazon",
                Enums.SpendCategory.SHOPPING);

        mockMvc.perform(get("/api/analytics/spending")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpend").value(4000.00))
                .andExpect(jsonPath("$.categories").isArray())
                // Largest category first: shopping at 2500 outranks food at 1500.
                .andExpect(jsonPath("$.categories[0].category").value("SHOPPING"))
                .andExpect(jsonPath("$.insights").isArray());
    }

    @Test
    @DisplayName("GET /api/analytics/spending rejects a malformed period")
    void analyticsValidatesPeriod() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("1000.00"));

        mockMvc.perform(get("/api/analytics/spending")
                        .param("period", "June-2026")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
    }

    @Test
    @DisplayName("GET /api/health-score returns a transparent breakdown, not a bare number")
    void healthScoreIsExplainable() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("40000.00"));

        mockMvc.perform(get("/api/health-score")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").exists())
                .andExpect(jsonPath("$.band").exists())
                .andExpect(jsonPath("$.indicators").isArray())
                .andExpect(jsonPath("$.indicators[0].explanation").exists())
                .andExpect(jsonPath("$.indicators[0].weight").exists())
                // The response must say this is not a credit score.
                .andExpect(jsonPath("$.interpretation")
                        .value(org.hamcrest.Matchers.containsString("not a credit score")));
    }

    @Test
    @DisplayName("POST /api/simulator/run projects without touching the balance")
    void simulatorDoesNotMoveMoney() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("25000.00"));
        String token = tokens.accessTokenFor(user);

        mockMvc.perform(post("/api/simulator/run")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "months", 12,
                                "monthlySavingAmount", new BigDecimal("3000.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months").value(12))
                .andExpect(jsonPath("$.series").isArray())
                .andExpect(jsonPath("$.assumptions").isArray())
                .andExpect(jsonPath("$.verdict").exists());

        mockMvc.perform(get("/api/accounts/" + user.account().getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.balance")
                        .value(25000.00));
    }

    @Test
    @DisplayName("POST /api/simulator/run rejects an out-of-range horizon")
    void simulatorValidatesHorizon() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("25000.00"));

        mockMvc.perform(post("/api/simulator/run")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("months", 999))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/copilot/ask answers from real data and reports its generator")
    void copilotAnswersHonestly() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("30000.00"),
                BigDecimal.ZERO, "CUSTOMER");

        ledgerService.postDebit(user.account().getId(), new BigDecimal("1800.00"),
                Enums.TransactionType.WITHDRAWAL, "Swiggy order", "Swiggy",
                Enums.SpendCategory.FOOD);

        mockMvc.perform(post("/api/copilot/ask")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "How much did I spend on food this month?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CATEGORY_SPEND"))
                // Honest reporting: this answer came from the rule engine.
                .andExpect(jsonPath("$.generator").value("rule_engine"))
                .andExpect(jsonPath("$.answer")
                        .value(org.hamcrest.Matchers.containsString("1800")))
                .andExpect(jsonPath("$.figures").exists())
                .andExpect(jsonPath("$.conversationId").exists());
    }

    @Test
    @DisplayName("POST /api/copilot/ask rejects an empty question")
    void copilotValidatesQuestion() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("1000.00"));

        mockMvc.perform(post("/api/copilot/ask")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("question", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.question").exists());
    }

    @Test
    @DisplayName("POST /api/savings-goals creates a goal with a derived monthly requirement")
    void createSavingsGoal() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("30000.00"));

        mockMvc.perform(post("/api/savings-goals")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Emergency fund",
                                "targetAmount", new BigDecimal("60000.00"),
                                "targetDate", LocalDate.now().plusMonths(6).toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Emergency fund"))
                .andExpect(jsonPath("$.progressPercent").value(0))
                .andExpect(jsonPath("$.requiredMonthly").exists())
                .andExpect(jsonPath("$.advice").exists());
    }

    @Test
    @DisplayName("POST /api/savings-goals rejects a target date in the past")
    void savingsGoalRejectsPastDate() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("30000.00"));

        mockMvc.perform(post("/api/savings-goals")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Backwards goal",
                                "targetAmount", new BigDecimal("1000.00"),
                                "targetDate", LocalDate.now().minusMonths(1).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.targetDate").exists());
    }

    @Test
    @DisplayName("GET /api/notifications returns the caller's notification centre")
    void notificationsAreScoped() throws Exception {
        var user = factory.customerWithAccount(new BigDecimal("1000.00"));

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + tokens.accessTokenFor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
