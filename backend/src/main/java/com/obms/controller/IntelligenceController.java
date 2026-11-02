package com.obms.controller;

import com.obms.domain.FraudAlert;
import com.obms.dto.AnalyticsDtos;
import com.obms.dto.CopilotDtos;
import com.obms.dto.HealthDtos;
import com.obms.dto.SavingsGoalDtos;
import com.obms.dto.SimulatorDtos;
import com.obms.security.CurrentUser;
import com.obms.service.AnalyticsService;
import com.obms.service.FinancialHealthService;
import com.obms.service.SavingsGoalService;
import com.obms.service.SimulatorService;
import com.obms.service.copilot.CopilotService;
import com.obms.service.fraud.FraudService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * The customer-facing intelligence surface: copilot, analytics, health score,
 * simulator, savings goals and the customer's own fraud alerts.
 *
 * <p>Grouped in one controller because these are all read-mostly views over the
 * same customer's own data, and because keeping them together makes the data
 * isolation argument easy to check: every method's first act is
 * {@code currentUser.requireCustomerId()}, and no method accepts a customer
 * identifier from the request.</p>
 */
@Tag(name = "Financial intelligence",
        description = "Copilot, spending analytics, health score, simulator, goals and alerts")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class IntelligenceController {

    private final CopilotService copilotService;
    private final AnalyticsService analyticsService;
    private final FinancialHealthService healthService;
    private final SimulatorService simulatorService;
    private final SavingsGoalService savingsGoalService;
    private final FraudService fraudService;
    private final CurrentUser currentUser;

    // ---- copilot -----------------------------------------------------

    @Operation(summary = "Ask the financial copilot a question",
            description = "Answers are computed from the signed-in customer's own ledger. "
                    + "The response reports which generator produced it.")
    @PostMapping("/copilot/ask")
    public CopilotDtos.AskResponse ask(@Valid @RequestBody CopilotDtos.AskRequest request) {
        return copilotService.ask(currentUser.requireCustomerId(), request);
    }

    @Operation(summary = "List copilot conversations")
    @GetMapping("/copilot/conversations")
    public List<CopilotDtos.ConversationSummary> conversations() {
        return copilotService.conversations(currentUser.requireCustomerId());
    }

    @Operation(summary = "Messages in one conversation")
    @GetMapping("/copilot/conversations/{conversationId}")
    public List<CopilotDtos.MessageView> messages(@PathVariable Long conversationId) {
        return copilotService.messages(currentUser.requireCustomerId(), conversationId);
    }

    @Operation(summary = "Starter prompts for an empty copilot panel")
    @GetMapping("/copilot/suggestions")
    public List<String> suggestions() {
        return copilotService.suggestedQuestions();
    }

    // ---- analytics ---------------------------------------------------

    @Operation(summary = "Spending analytics for a month",
            description = "Period defaults to the current month. Own-account transfers are "
                    + "excluded from both income and spending.")
    @GetMapping("/analytics/spending")
    public AnalyticsDtos.SpendingAnalytics spending(
            @RequestParam(required = false) String period) {
        return analyticsService.analyse(currentUser.requireCustomerId(), parsePeriod(period));
    }

    // ---- financial health --------------------------------------------

    @Operation(summary = "Financial wellness score",
            description = "A simulated personal wellness score computed from OBMS activity. "
                    + "Not a credit score and not shared with any bureau.")
    @GetMapping("/health-score")
    public HealthDtos.FinancialHealth healthScore(
            @RequestParam(required = false) String period) {
        return healthService.evaluate(currentUser.requireCustomerId(), parsePeriod(period));
    }

    @Operation(summary = "Score history for the trend chart")
    @GetMapping("/health-score/history")
    public List<HealthDtos.ScorePoint> healthHistory() {
        return healthService.history(currentUser.requireCustomerId());
    }

    // ---- what-if simulator -------------------------------------------

    @Operation(summary = "Run a what-if projection",
            description = "Read-only. This endpoint cannot move money or change a balance.")
    @PostMapping("/simulator/run")
    public SimulatorDtos.SimulationResult simulate(
            @Valid @RequestBody SimulatorDtos.SimulationRequest request) {
        return simulatorService.simulate(currentUser.requireCustomerId(), request);
    }

    @Operation(summary = "Check whether a monthly saving is affordable")
    @GetMapping("/simulator/affordability")
    public SimulatorDtos.AffordabilityResult affordability(@RequestParam BigDecimal amount) {
        return simulatorService.canAfford(currentUser.requireCustomerId(), amount);
    }

    // ---- savings goals -----------------------------------------------

    @Operation(summary = "List savings goals with derived requirements")
    @GetMapping("/savings-goals")
    public List<SavingsGoalDtos.GoalView> goals() {
        return savingsGoalService.list(currentUser.requireCustomerId());
    }

    @Operation(summary = "Create a savings goal")
    @PostMapping("/savings-goals")
    public ResponseEntity<SavingsGoalDtos.GoalView> createGoal(
            @Valid @RequestBody SavingsGoalDtos.CreateGoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savingsGoalService.create(currentUser.requireCustomerId(), request));
    }

    @Operation(summary = "Contribute to a goal",
            description = "Debits the chosen account, so the goal and the balance stay "
                    + "consistent and the contribution appears on the statement.")
    @PostMapping("/savings-goals/{goalId}/contribute")
    public SavingsGoalDtos.GoalView contribute(
            @PathVariable Long goalId,
            @Valid @RequestBody SavingsGoalDtos.ContributeRequest request) {
        return savingsGoalService.contribute(currentUser.requireCustomerId(), goalId, request);
    }

    @Operation(summary = "Abandon a goal")
    @DeleteMapping("/savings-goals/{goalId}")
    public ResponseEntity<Void> abandonGoal(@PathVariable Long goalId) {
        savingsGoalService.abandon(currentUser.requireCustomerId(), goalId);
        return ResponseEntity.noContent().build();
    }

    // ---- the customer's own fraud alerts ------------------------------

    @Operation(summary = "The customer's fraud alerts, newest first")
    @GetMapping("/fraud-alerts")
    public Page<AlertView> alerts(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return fraudService
                .alertsFor(currentUser.requireCustomerId(),
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)))
                .map(AlertView::from);
    }

    @Operation(summary = "Report an alert as a false positive",
            description = "Recorded as model feedback. The flag is stored, not used to "
                    + "retrain automatically.")
    @PostMapping("/fraud-alerts/{alertId}/false-positive")
    public AlertView markFalsePositive(@PathVariable Long alertId,
                                       @RequestBody(required = false) Map<String, String> body) {
        String note = body == null ? null : body.get("note");
        return AlertView.from(fraudService.markFalsePositive(
                alertId, currentUser.requireCustomerId(), note));
    }

    private YearMonth parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(period.trim());
        } catch (java.time.format.DateTimeParseException ex) {
            throw com.obms.common.ApiException.badRequest("INVALID_PERIOD",
                    "Period must be in yyyy-MM form, for example 2026-10.");
        }
    }

    /**
     * A fraud alert as its owner sees it.
     *
     * <p>Carries the explanation, never a bare score - the whole point of the
     * explainable model is that the customer can see why.</p>
     */
    public record AlertView(
            Long id,
            String evaluatedRef,
            BigDecimal amount,
            int riskScore,
            String riskLevel,
            String reason,
            String factorsJson,
            String decision,
            String status,
            boolean markedFalsePositive,
            java.time.Instant raisedAt
    ) {
        static AlertView from(FraudAlert alert) {
            return new AlertView(alert.getId(), alert.getEvaluatedRef(), alert.getAmount(),
                    alert.getRiskScore(), alert.getRiskLevel().name(), alert.getPrimaryReason(),
                    alert.getExplanationJson(), alert.getDecision(), alert.getStatus().name(),
                    alert.isMarkedFalsePositive(), alert.getCreatedAt());
        }
    }
}
