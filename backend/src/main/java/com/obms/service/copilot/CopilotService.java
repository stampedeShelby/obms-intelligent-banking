package com.obms.service.copilot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obms.common.ApiException;
import com.obms.domain.Account;
import com.obms.domain.CopilotConversation;
import com.obms.domain.CopilotMessage;
import com.obms.domain.Customer;
import com.obms.domain.Enums;
import com.obms.domain.FraudAlert;
import com.obms.dto.AnalyticsDtos;
import com.obms.dto.CopilotDtos;
import com.obms.dto.HealthDtos;
import com.obms.dto.SavingsGoalDtos;
import com.obms.dto.SimulatorDtos;
import com.obms.repository.AccountRepository;
import com.obms.repository.CopilotConversationRepository;
import com.obms.repository.CopilotMessageRepository;
import com.obms.repository.CustomerRepository;
import com.obms.service.AccountService;
import com.obms.service.AnalyticsService;
import com.obms.service.FinancialHealthService;
import com.obms.service.SavingsGoalService;
import com.obms.service.SimulatorService;
import com.obms.service.fraud.FraudService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The AI Financial Copilot (innovation module 1).
 *
 * <h2>Where the answers come from</h2>
 * <p>Every figure the copilot states is computed in Java from the
 * authenticated customer's own ledger, by the same services that power the
 * analytics, health and simulator screens. The copilot does not have a
 * separate source of truth, so it cannot contradict the dashboard.</p>
 *
 * <h2>Why there is no hardcoded answer anywhere</h2>
 * <p>The requirement was explicit: do not fake AI. So the design is honest in
 * both directions. With no language model configured, the copilot runs on
 * intent detection plus real analytics and reports {@code generator =
 * rule_engine}. It is a working natural-language interface over real data, and
 * it says so. If a model is later wired in, it is given the computed figures
 * and asked only to phrase them - it is never the source of a number.</p>
 *
 * <h2>Data isolation</h2>
 * <p>Every lookup is scoped by the customer id taken from the verified token.
 * A question like "show me account 100248573910's balance" cannot reach another
 * customer's data, because the account number in the question is never used as
 * a lookup key - only the token's customer id is.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CopilotService {

    private static final String GENERATOR_RULES = "rule_engine";
    private static final int MAX_CONVERSATION_TITLE = 80;

    private final CopilotConversationRepository conversationRepository;
    private final CopilotMessageRepository messageRepository;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final AnalyticsService analyticsService;
    private final SimulatorService simulatorService;
    private final FinancialHealthService healthService;
    private final SavingsGoalService savingsGoalService;
    private final FraudService fraudService;
    private final AccountService accountService;
    private final ObjectMapper objectMapper;

    /**
     * Answers a question and records the turn.
     */
    @Transactional
    public CopilotDtos.AskResponse ask(Long customerId, CopilotDtos.AskRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Customer"));

        CopilotConversation conversation = resolveConversation(customer, request);
        persistMessage(conversation, Enums.MessageRole.USER, request.question(), null, null);

        CopilotIntent intent = CopilotIntent.detect(request.question());
        Answer answer = answerFor(customerId, request.question(), intent);

        persistMessage(conversation, Enums.MessageRole.ASSISTANT, answer.prose(),
                intent.name(), answer.figures());

        return new CopilotDtos.AskResponse(
                conversation.getId(), answer.prose(), intent.name(), answer.figures(),
                answer.chart(), GENERATOR_RULES, answer.dataAvailable(), Instant.now());
    }

    @Transactional(readOnly = true)
    public List<CopilotDtos.ConversationSummary> conversations(Long customerId) {
        return conversationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(conversation -> new CopilotDtos.ConversationSummary(
                        conversation.getId(), conversation.getTitle(), conversation.getCreatedAt(),
                        messageRepository
                                .findByConversationIdOrderByCreatedAtAsc(conversation.getId())
                                .size()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CopilotDtos.MessageView> messages(Long customerId, Long conversationId) {
        // Scoped by customer: another customer's thread id resolves to nothing.
        conversationRepository.findByIdAndCustomerId(conversationId, customerId)
                .orElseThrow(() -> ApiException.notFound("Conversation"));

        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(message -> new CopilotDtos.MessageView(
                        message.getRole().name(), message.getContent(),
                        message.getIntent(), message.getCreatedAt()))
                .toList();
    }

    /** Starter prompts shown in an empty copilot panel. */
    public List<String> suggestedQuestions() {
        return List.of(
                "How much did I spend on food this month?",
                "What are my biggest spending categories?",
                "Why are my expenses higher this month?",
                "How much can I save if I reduce shopping by 15%?",
                "Can I afford to save 5000 every month?",
                "How is my financial health?",
                "Summarise my last three months of transactions.");
    }

    // ------------------------------------------------------------------
    // intent handlers
    // ------------------------------------------------------------------

    private Answer answerFor(Long customerId, String question, CopilotIntent intent) {
        return switch (intent) {
            case CATEGORY_SPEND -> answerCategorySpend(customerId, question);
            case TOP_CATEGORIES -> answerTopCategories(customerId);
            case SPEND_COMPARISON -> answerSpendComparison(customerId);
            case PERIOD_SUMMARY -> answerPeriodSummary(customerId, question);
            case SAVING_SIMULATION -> answerSavingSimulation(customerId, question);
            case AFFORDABILITY -> answerAffordability(customerId, question);
            case BALANCE -> answerBalance(customerId);
            case HEALTH_SCORE -> answerHealthScore(customerId);
            case RECENT_TRANSACTIONS -> answerRecentTransactions(customerId);
            case FRAUD_STATUS -> answerFraudStatus(customerId);
            case GOAL_STATUS -> answerGoalStatus(customerId);
            case UNKNOWN -> answerUnknown();
        };
    }

    private Answer answerCategorySpend(Long customerId, String question) {
        AnalyticsDtos.SpendingAnalytics analytics = analyticsService.analyse(customerId, null);
        Optional<Enums.SpendCategory> category = CopilotIntent.extractCategory(question);

        if (category.isEmpty()) {
            if (analytics.totalSpend().signum() == 0) {
                return noData("I could not find any spending recorded this month.");
            }
            Map<String, String> figures = new LinkedHashMap<>();
            figures.put("Total spending this month", money(analytics.totalSpend()));
            figures.put("Period", analytics.period());
            return new Answer(
                    "You have spent " + money(analytics.totalSpend()) + " in total this month ("
                            + analytics.period() + "). Name a category - food, shopping, "
                            + "transport and so on - and I will break that down for you.",
                    figures, categoryChart(analytics), true);
        }

        Enums.SpendCategory target = category.get();
        Optional<AnalyticsDtos.CategorySlice> slice = analytics.categories().stream()
                .filter(item -> item.category() == target)
                .findFirst();

        if (slice.isEmpty()) {
            Map<String, String> figures = new LinkedHashMap<>();
            figures.put("Category", target.name());
            figures.put("Spending this month", money(BigDecimal.ZERO));
            return new Answer(
                    "You have not spent anything on " + friendly(target) + " this month ("
                            + analytics.period() + ").",
                    figures, null, true);
        }

        AnalyticsDtos.CategorySlice found = slice.get();
        Map<String, String> figures = new LinkedHashMap<>();
        figures.put("Category", friendly(target));
        figures.put("Spent this month", money(found.total()));
        figures.put("Share of total spending", found.share() + "%");
        figures.put("Number of transactions", String.valueOf(found.transactions()));
        figures.put("Total spending this month", money(analytics.totalSpend()));

        return new Answer(
                "You have spent " + money(found.total()) + " on " + friendly(target)
                        + " this month across " + found.transactions()
                        + (found.transactions() == 1 ? " transaction" : " transactions")
                        + ". That is " + found.share() + "% of your "
                        + money(analytics.totalSpend()) + " total spending.",
                figures, categoryChart(analytics), true);
    }

    private Answer answerTopCategories(Long customerId) {
        AnalyticsDtos.SpendingAnalytics analytics = analyticsService.analyse(customerId, null);
        if (analytics.categories().isEmpty()) {
            return noData("There is no spending recorded this month to break down yet.");
        }

        List<AnalyticsDtos.CategorySlice> top = analytics.categories().stream().limit(3).toList();
        StringBuilder prose = new StringBuilder("Your largest spending categories this month are ");
        Map<String, String> figures = new LinkedHashMap<>();

        for (int i = 0; i < top.size(); i++) {
            AnalyticsDtos.CategorySlice slice = top.get(i);
            if (i > 0) {
                prose.append(i == top.size() - 1 ? " and " : ", ");
            }
            prose.append(friendly(slice.category())).append(" at ").append(money(slice.total()))
                    .append(" (").append(slice.share()).append("%)");
            figures.put(friendly(slice.category()), money(slice.total()) + " (" + slice.share() + "%)");
        }
        prose.append(". Total spending is ").append(money(analytics.totalSpend())).append('.');
        figures.put("Total spending", money(analytics.totalSpend()));

        return new Answer(prose.toString(), figures, categoryChart(analytics), true);
    }

    private Answer answerSpendComparison(Long customerId) {
        AnalyticsDtos.SpendingAnalytics analytics = analyticsService.analyse(customerId, null);
        List<AnalyticsDtos.MonthlyTotals> trend = analytics.trend();

        if (trend.size() < 2) {
            return noData("I need at least two months of history to compare against.");
        }

        AnalyticsDtos.MonthlyTotals current = trend.get(trend.size() - 1);
        AnalyticsDtos.MonthlyTotals previous = trend.get(trend.size() - 2);
        BigDecimal delta = current.expense().subtract(previous.expense());

        Map<String, String> figures = new LinkedHashMap<>();
        figures.put("This month (" + current.period() + ")", money(current.expense()));
        figures.put("Last month (" + previous.period() + ")", money(previous.expense()));
        figures.put("Change", money(delta));
        figures.put("Change (%)", analytics.monthOverMonth() + "%");

        String direction = delta.signum() > 0 ? "higher" : delta.signum() < 0 ? "lower" : "unchanged";
        StringBuilder prose = new StringBuilder();
        prose.append("Your spending this month is ").append(money(current.expense()))
                .append(", which is ").append(money(delta.abs())).append(' ').append(direction)
                .append(" than last month's ").append(money(previous.expense()))
                .append(" (").append(analytics.monthOverMonth()).append("%).");

        if (!analytics.categories().isEmpty()) {
            AnalyticsDtos.CategorySlice biggest = analytics.categories().get(0);
            prose.append(" The largest contributor is ").append(friendly(biggest.category()))
                    .append(" at ").append(money(biggest.total())).append('.');
        }

        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (AnalyticsDtos.MonthlyTotals month : trend) {
            labels.add(month.period());
            values.add(month.expense().doubleValue());
        }

        return new Answer(prose.toString(), figures,
                new CopilotDtos.ChartData("line", "Monthly spending", labels, values), true);
    }

    private Answer answerPeriodSummary(Long customerId, String question) {
        int months = CopilotIntent.extractMonths(question);
        AnalyticsDtos.SpendingAnalytics analytics = analyticsService.analyse(customerId, null);
        List<AnalyticsDtos.MonthlyTotals> trend = analytics.trend();

        List<AnalyticsDtos.MonthlyTotals> window = trend.size() <= months
                ? trend
                : trend.subList(trend.size() - months, trend.size());

        BigDecimal income = window.stream().map(AnalyticsDtos.MonthlyTotals::income)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expense = window.stream().map(AnalyticsDtos.MonthlyTotals::expense)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (income.signum() == 0 && expense.signum() == 0) {
            return noData("There is no activity recorded in that period.");
        }

        BigDecimal net = income.subtract(expense);
        Map<String, String> figures = new LinkedHashMap<>();
        figures.put("Months covered", String.valueOf(window.size()));
        figures.put("Total received", money(income));
        figures.put("Total spent", money(expense));
        figures.put("Net", money(net));

        String verdict = net.signum() >= 0
                ? "You kept " + money(net) + " over that period."
                : "You spent " + money(net.abs()) + " more than you received over that period.";

        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (AnalyticsDtos.MonthlyTotals month : window) {
            labels.add(month.period());
            values.add(month.expense().doubleValue());
        }

        return new Answer(
                "Across the last " + window.size()
                        + (window.size() == 1 ? " month" : " months")
                        + " you received " + money(income) + " and spent " + money(expense)
                        + ". " + verdict,
                figures, new CopilotDtos.ChartData("bar", "Spending by month", labels, values), true);
    }

    private Answer answerSavingSimulation(Long customerId, String question) {
        Optional<Enums.SpendCategory> category = CopilotIntent.extractCategory(question);
        Optional<BigDecimal> percent = CopilotIntent.extractPercent(question);
        Optional<BigDecimal> amount = CopilotIntent.extractAmount(question);

        SimulatorDtos.SimulationRequest simulation = new SimulatorDtos.SimulationRequest(
                12,
                amount.orElse(null),
                null,
                null,
                category.orElse(null),
                category.isPresent() ? percent.orElse(BigDecimal.TEN) : null);

        SimulatorDtos.SimulationResult result = simulatorService.simulate(customerId, simulation);

        if (!result.dataSufficient()) {
            return noData("I need a couple of months of transaction history before a "
                    + "projection would be meaningful.");
        }

        Map<String, String> figures = new LinkedHashMap<>();
        figures.put("Horizon", result.months() + " months");
        figures.put("Current monthly net", money(result.current().monthlyNet()));
        figures.put("Proposed monthly net", money(result.proposed().monthlyNet()));
        figures.put("Accumulated per month", money(result.monthlyAccumulation()));
        figures.put("Difference over horizon", money(result.difference()));

        StringBuilder prose = new StringBuilder(result.verdict());
        prose.append(" This assumes: ").append(String.join(" ", result.assumptions()));

        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (SimulatorDtos.ProjectionPoint point : result.series()) {
            labels.add(point.period());
            values.add(point.projected().doubleValue());
        }

        return new Answer(prose.toString(), figures,
                new CopilotDtos.ChartData("line", "Projected balance", labels, values), true);
    }

    private Answer answerAffordability(Long customerId, String question) {
        Optional<BigDecimal> amount = CopilotIntent.extractAmount(question);
        if (amount.isEmpty()) {
            return new Answer(
                    "Tell me the monthly amount you have in mind - for example "
                            + "\"can I afford to save 5000 every month\" - and I will check it "
                            + "against your actual surplus.",
                    Map.of(), null, true);
        }

        SimulatorDtos.AffordabilityResult result =
                simulatorService.canAfford(customerId, amount.get());

        Map<String, String> figures = new LinkedHashMap<>();
        figures.put("Amount asked about", money(result.requestedAmount()));
        figures.put("Your monthly surplus", money(result.monthlySurplus()));
        figures.put("Sustainable suggestion", money(result.suggestedAmount()));
        figures.put("Affordable", result.affordable() ? "Yes" : "No");

        String lead = result.affordable()
                ? "Yes, that looks affordable. "
                : "That would be a stretch. ";

        return new Answer(lead + result.reasoning(), figures, null, result.dataSufficient());
    }

    private Answer answerBalance(Long customerId) {
        List<Account> accounts = accountRepository.findByCustomerIdOrderByCreatedAtAsc(customerId);
        if (accounts.isEmpty()) {
            return noData("You do not have any accounts open yet.");
        }

        BigDecimal total = accounts.stream().map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, String> figures = new LinkedHashMap<>();
        for (Account account : accounts) {
            figures.put(account.getAccountType() + " " + maskAccount(account.getAccountNumber()),
                    money(account.getBalance()));
        }
        figures.put("Total", money(total));

        String detail = accounts.size() == 1
                ? "Your " + friendlyAccount(accounts.get(0)) + " holds " + money(total) + "."
                : "Across " + accounts.size() + " accounts you hold " + money(total) + " in total.";

        return new Answer(detail, figures, null, true);
    }

    private Answer answerHealthScore(Long customerId) {
        HealthDtos.FinancialHealth health = healthService.evaluate(customerId, null);

        Map<String, String> figures = new LinkedHashMap<>();
        figures.put("Score", health.score() + " / 100");
        figures.put("Band", health.band());
        for (HealthDtos.IndicatorScore indicator : health.indicators()) {
            figures.put(indicator.label(), indicator.points() + " / " + indicator.weight());
        }

        StringBuilder prose = new StringBuilder("Your financial wellness score is ")
                .append(health.score()).append(" out of 100 (").append(health.band())
                .append("). ").append(health.interpretation());

        if (!health.suggestions().isEmpty()) {
            HealthDtos.Suggestion first = health.suggestions().get(0);
            prose.append(" The biggest single improvement available: ")
                    .append(first.action()).append(" - ").append(first.rationale());
        }

        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (HealthDtos.IndicatorScore indicator : health.indicators()) {
            labels.add(indicator.label());
            values.add((double) indicator.points());
        }

        return new Answer(prose.toString(), figures,
                new CopilotDtos.ChartData("bar", "Score by indicator", labels, values),
                health.dataSufficient());
    }

    private Answer answerRecentTransactions(Long customerId) {
        var recent = accountService.recentAcrossAccounts(customerId);
        if (recent.isEmpty()) {
            return noData("There are no transactions on your accounts yet.");
        }

        Map<String, String> figures = new LinkedHashMap<>();
        StringBuilder prose = new StringBuilder("Your most recent transactions: ");
        int shown = 0;
        for (var txn : recent) {
            if (shown >= 5) {
                break;
            }
            String label = (txn.direction() == Enums.TransactionDirection.DEBIT ? "-" : "+")
                    + money(txn.amount());
            figures.put(txn.reference(), label + " " + nullSafe(txn.description()));
            if (shown > 0) {
                prose.append("; ");
            }
            prose.append(label).append(' ').append(nullSafe(txn.description()));
            shown++;
        }
        prose.append('.');

        return new Answer(prose.toString(), figures, null, true);
    }

    private Answer answerFraudStatus(Long customerId) {
        List<FraudAlert> alerts = fraudService.activeAlertsFor(customerId);

        if (alerts.isEmpty()) {
            return new Answer(
                    "There are no open fraud alerts on your account. Every transfer is scored "
                            + "before it settles, and anything unusual would appear here.",
                    Map.of("Open alerts", "0"), null, true);
        }

        Map<String, String> figures = new LinkedHashMap<>();
        figures.put("Open alerts", String.valueOf(alerts.size()));
        StringBuilder prose = new StringBuilder("You have ").append(alerts.size())
                .append(alerts.size() == 1 ? " open alert" : " open alerts").append(". ");

        for (FraudAlert alert : alerts.stream().limit(3).toList()) {
            figures.put(alert.getRiskLevel() + " - " + money(alert.getAmount()),
                    alert.getPrimaryReason());
            prose.append(alert.getRiskLevel()).append(" risk on ")
                    .append(money(alert.getAmount())).append(": ")
                    .append(alert.getPrimaryReason()).append(' ');
        }

        return new Answer(prose.toString().trim(), figures, null, true);
    }

    private Answer answerGoalStatus(Long customerId) {
        List<SavingsGoalDtos.GoalView> goals = savingsGoalService.list(customerId).stream()
                .filter(goal -> goal.status() == Enums.GoalStatus.ACTIVE)
                .toList();

        if (goals.isEmpty()) {
            return new Answer(
                    "You do not have any active savings goals. Creating one gives the health "
                            + "score something to track and makes the simulator more useful.",
                    Map.of("Active goals", "0"), null, true);
        }

        Map<String, String> figures = new LinkedHashMap<>();
        StringBuilder prose = new StringBuilder();

        for (SavingsGoalDtos.GoalView goal : goals) {
            figures.put(goal.name(), money(goal.savedAmount()) + " of "
                    + money(goal.targetAmount()) + " (" + goal.progressPercent() + "%)");
            prose.append(goal.name()).append(" is at ").append(goal.progressPercent())
                    .append("% - ").append(goal.advice()).append(' ');
        }

        List<String> labels = goals.stream().map(SavingsGoalDtos.GoalView::name).toList();
        List<Double> values = goals.stream()
                .map(goal -> goal.progressPercent().doubleValue()).toList();

        return new Answer(prose.toString().trim(), figures,
                new CopilotDtos.ChartData("bar", "Goal progress (%)", labels, values), true);
    }

    private Answer answerUnknown() {
        return new Answer(
                "I can answer questions about your spending, balances, financial health, "
                        + "savings goals and fraud alerts, and I can run what-if projections. "
                        + "Try asking how much you spent on a category this month, or whether "
                        + "you can afford to save a particular amount.",
                Map.of(), null, true);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private CopilotConversation resolveConversation(Customer customer,
                                                     CopilotDtos.AskRequest request) {
        if (request.conversationId() != null) {
            return conversationRepository
                    .findByIdAndCustomerId(request.conversationId(), customer.getId())
                    .orElseThrow(() -> ApiException.notFound("Conversation"));
        }
        CopilotConversation conversation = new CopilotConversation();
        conversation.setCustomer(customer);
        conversation.setTitle(truncate(request.question(), MAX_CONVERSATION_TITLE));
        return conversationRepository.save(conversation);
    }

    private void persistMessage(CopilotConversation conversation, Enums.MessageRole role,
                                String content, String intent, Map<String, String> figures) {
        CopilotMessage message = new CopilotMessage();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        message.setIntent(intent);
        message.setGenerator(role == Enums.MessageRole.ASSISTANT ? GENERATOR_RULES : null);
        if (figures != null && !figures.isEmpty()) {
            try {
                message.setDataJson(objectMapper.writeValueAsString(figures));
            } catch (JsonProcessingException ex) {
                log.warn("Could not serialise copilot figures", ex);
            }
        }
        messageRepository.save(message);
    }

    private CopilotDtos.ChartData categoryChart(AnalyticsDtos.SpendingAnalytics analytics) {
        if (analytics.categories().isEmpty()) {
            return null;
        }
        List<String> labels = analytics.categories().stream()
                .map(slice -> friendly(slice.category())).toList();
        List<Double> values = analytics.categories().stream()
                .map(slice -> slice.total().doubleValue()).toList();
        return new CopilotDtos.ChartData("pie", "Spending by category", labels, values);
    }

    private Answer noData(String message) {
        return new Answer(message, Map.of(), null, false);
    }

    private static String money(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return "INR " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String friendly(Enums.SpendCategory category) {
        String name = category.name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return name.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + name.substring(1);
    }

    private static String friendlyAccount(Account account) {
        return account.getAccountType().name().toLowerCase(java.util.Locale.ROOT) + " account";
    }

    private static String maskAccount(String accountNumber) {
        return accountNumber.length() <= 4
                ? accountNumber
                : "..." + accountNumber.substring(accountNumber.length() - 4);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    /**
     * An answer before it becomes a response.
     *
     * @param prose         the sentence or two the customer reads
     * @param figures       the numbers the prose is built from
     * @param chart         optional chart data
     * @param dataAvailable false when there was nothing to answer from
     */
    private record Answer(String prose, Map<String, String> figures,
                          CopilotDtos.ChartData chart, boolean dataAvailable) {
    }
}
