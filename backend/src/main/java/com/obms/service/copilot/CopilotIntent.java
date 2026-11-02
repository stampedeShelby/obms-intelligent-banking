package com.obms.service.copilot;

import com.obms.domain.Enums;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intent classification for copilot questions.
 *
 * <p>This is the deterministic layer that makes the copilot work with no
 * language model present. It recognises what the customer is asking, and the
 * answer is then computed from their ledger by {@link CopilotService}. The
 * split matters: classification can be wrong and recoverable, but a figure
 * must never be invented, so figures never come from a model.</p>
 */
public enum CopilotIntent {

    /** "How much did I spend on food this month?" */
    CATEGORY_SPEND,
    /** "What are my biggest spending categories?" */
    TOP_CATEGORIES,
    /** "Why are my expenses higher this month?" */
    SPEND_COMPARISON,
    /** "Summarise my last three months." */
    PERIOD_SUMMARY,
    /** "How much can I save if I cut shopping by 15%?" */
    SAVING_SIMULATION,
    /** "Can I afford to save 5000 every month?" */
    AFFORDABILITY,
    /** "What is my balance?" */
    BALANCE,
    /** "How is my financial health?" */
    HEALTH_SCORE,
    /** "Show my recent transactions." */
    RECENT_TRANSACTIONS,
    /** "Any suspicious activity?" */
    FRAUD_STATUS,
    /** "How are my savings goals doing?" */
    GOAL_STATUS,
    /** Nothing matched. */
    UNKNOWN;

    private static final Pattern PERCENT = Pattern.compile("(\\d{1,3}(?:\\.\\d+)?)\\s*%");
    private static final Pattern AMOUNT = Pattern.compile(
            "(?:₹|rs\\.?|inr)?\\s*(\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)");
    private static final Pattern MONTH_COUNT = Pattern.compile(
            "(?:last|past|previous)\\s+(\\d{1,2}|one|two|three|four|five|six|twelve)\\s+months?");

    /**
     * Classifies a question.
     *
     * <p>Order is significant. More specific intents are tested first, because
     * "how much can I save if I cut food by 10%" mentions both a category and a
     * simulation, and the simulation is what the customer actually wants.</p>
     */
    public static CopilotIntent detect(String question) {
        String text = question.toLowerCase(Locale.ROOT);

        if (containsAny(text, "what if", "if i reduce", "if i cut", "if i save", "if i increase",
                "if i decrease", "how much can i save", "how much will i", "how much would i")) {
            return SAVING_SIMULATION;
        }
        if (containsAny(text, "can i afford", "is it realistic", "affordable", "can i manage")) {
            return AFFORDABILITY;
        }
        if (containsAny(text, "why are my expenses", "why is my spending", "compared to last",
                "versus last month", "vs last month", "higher this month", "more than last month",
                "spending gone up", "spending increase")) {
            return SPEND_COMPARISON;
        }
        if (containsAny(text, "biggest", "largest", "top categor", "most on", "where does my money",
                "breakdown", "where is my money going")) {
            return TOP_CATEGORIES;
        }
        if (containsAny(text, "summar", "overview", "how did i do", "recap")) {
            return PERIOD_SUMMARY;
        }
        if (containsAny(text, "financial health", "health score", "wellness", "how am i doing financially")) {
            return HEALTH_SCORE;
        }
        if (containsAny(text, "suspicious", "fraud", "unusual activity", "unauthorised", "unauthorized",
                "alert")) {
            return FRAUD_STATUS;
        }
        if (containsAny(text, "savings goal", "my goals", "goal progress", "on track for")) {
            return GOAL_STATUS;
        }
        if (containsAny(text, "balance", "how much do i have", "how much money")) {
            return BALANCE;
        }
        if (containsAny(text, "recent transaction", "last transaction", "latest transaction",
                "recent activity", "what did i spend on recently")) {
            return RECENT_TRANSACTIONS;
        }
        if (containsAny(text, "how much did i spend", "how much have i spent", "spent on",
                "spending on", "did i spend")) {
            return CATEGORY_SPEND;
        }
        return UNKNOWN;
    }

    /**
     * Finds a spend category named in the question.
     *
     * @return the category, or empty when none is named
     */
    public static Optional<Enums.SpendCategory> extractCategory(String question) {
        String text = question.toLowerCase(Locale.ROOT);

        if (containsAny(text, "food", "eating", "restaurant", "grocer", "dining", "swiggy", "zomato")) {
            return Optional.of(Enums.SpendCategory.FOOD);
        }
        if (containsAny(text, "shopping", "shop", "clothes", "amazon", "flipkart", "apparel")) {
            return Optional.of(Enums.SpendCategory.SHOPPING);
        }
        if (containsAny(text, "utilit", "electricity", "water bill", "gas bill", "broadband",
                "internet", "recharge", "bills")) {
            return Optional.of(Enums.SpendCategory.UTILITIES);
        }
        if (containsAny(text, "transport", "travel", "uber", "ola", "fuel", "petrol", "commute",
                "cab", "metro")) {
            return Optional.of(Enums.SpendCategory.TRANSPORT);
        }
        if (containsAny(text, "health", "medical", "pharmacy", "doctor", "hospital")) {
            return Optional.of(Enums.SpendCategory.HEALTHCARE);
        }
        if (containsAny(text, "entertainment", "netflix", "movie", "subscription", "spotify",
                "cinema")) {
            return Optional.of(Enums.SpendCategory.ENTERTAINMENT);
        }
        if (containsAny(text, "education", "course", "tuition", "college", "school", "books")) {
            return Optional.of(Enums.SpendCategory.EDUCATION);
        }
        if (containsAny(text, "rent", "landlord", "society")) {
            return Optional.of(Enums.SpendCategory.RENT);
        }
        return Optional.empty();
    }

    /**
     * @return a percentage mentioned in the question, if any
     */
    public static Optional<BigDecimal> extractPercent(String question) {
        Matcher matcher = PERCENT.matcher(question);
        if (matcher.find()) {
            try {
                return Optional.of(new BigDecimal(matcher.group(1)));
            } catch (NumberFormatException ex) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * @return a rupee amount mentioned in the question, if any
     */
    public static Optional<BigDecimal> extractAmount(String question) {
        // Skip anything that is part of a percentage, which extractPercent owns.
        String stripped = PERCENT.matcher(question).replaceAll(" ");
        Matcher matcher = AMOUNT.matcher(stripped.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String raw = matcher.group(1).replace(",", "");
            try {
                BigDecimal value = new BigDecimal(raw);
                // Ignore small integers that are almost certainly month counts.
                if (value.compareTo(BigDecimal.valueOf(100)) >= 0) {
                    return Optional.of(value);
                }
            } catch (NumberFormatException ex) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * @return the number of months the question refers to, defaulting to 1
     */
    public static int extractMonths(String question) {
        Matcher matcher = MONTH_COUNT.matcher(question.toLowerCase(Locale.ROOT));
        if (matcher.find()) {
            String token = matcher.group(1);
            return switch (token) {
                case "one" -> 1;
                case "two" -> 2;
                case "three" -> 3;
                case "four" -> 4;
                case "five" -> 5;
                case "six" -> 6;
                case "twelve" -> 12;
                default -> clampMonths(token);
            };
        }
        return 1;
    }

    private static int clampMonths(String token) {
        try {
            int value = Integer.parseInt(token);
            return Math.max(1, Math.min(12, value));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private static boolean containsAny(String text, String... needles) {
        return List.of(needles).stream().anyMatch(text::contains);
    }
}
