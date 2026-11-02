package com.obms.unit;

import com.obms.domain.Enums;
import com.obms.service.copilot.CopilotIntent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for copilot intent detection and parameter extraction.
 *
 * <p>These cover the example questions named in the project specification, so
 * the demonstration script is backed by assertions rather than hope.</p>
 */
@DisplayName("Unit: copilot intent detection")
class CopilotIntentTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "'How much did I spend on food this month?',CATEGORY_SPEND",
            "'What are my biggest spending categories?',TOP_CATEGORIES",
            "'Why are my expenses higher this month?',SPEND_COMPARISON",
            "'Summarize my last three months of transactions.',PERIOD_SUMMARY",
            "'How much can I save if I reduce shopping expenses by 15%?',SAVING_SIMULATION",
            "'Can I afford to save 5000 every month?',AFFORDABILITY",
            "'What is my balance?',BALANCE",
            "'How is my financial health?',HEALTH_SCORE",
            "'Show me my recent transactions',RECENT_TRANSACTIONS",
            "'Any suspicious activity on my account?',FRAUD_STATUS",
            "'How are my savings goals doing?',GOAL_STATUS"
    })
    @DisplayName("detects the specification's example questions")
    void detectsSpecificationExamples(String question, CopilotIntent expected) {
        assertThat(CopilotIntent.detect(question)).isEqualTo(expected);
    }

    @Test
    @DisplayName("a simulation question beats the category it mentions")
    void simulationOutranksCategory() {
        // "food" would match CATEGORY_SPEND, but the customer is asking a
        // what-if question, which is the more specific intent.
        assertThat(CopilotIntent.detect("How much can I save if I cut food by 20%?"))
                .isEqualTo(CopilotIntent.SAVING_SIMULATION);
    }

    @Test
    @DisplayName("unrecognised questions return UNKNOWN rather than a wrong guess")
    void unknownQuestions() {
        assertThat(CopilotIntent.detect("What is the capital of France?"))
                .isEqualTo(CopilotIntent.UNKNOWN);
    }

    @ParameterizedTest(name = "\"{0}\" names the {1} category")
    @CsvSource({
            "'spend on food',FOOD",
            "'my shopping habits',SHOPPING",
            "'electricity bill',UTILITIES",
            "'uber and petrol',TRANSPORT",
            "'pharmacy spending',HEALTHCARE",
            "'netflix and movies',ENTERTAINMENT",
            "'tuition and books',EDUCATION",
            "'my rent',RENT"
    })
    @DisplayName("extracts a named spend category")
    void extractsCategory(String question, Enums.SpendCategory expected) {
        assertThat(CopilotIntent.extractCategory(question)).contains(expected);
    }

    @Test
    @DisplayName("returns empty when no category is named")
    void noCategoryNamed() {
        assertThat(CopilotIntent.extractCategory("how much did I spend")).isEmpty();
    }

    @Test
    @DisplayName("extracts a percentage")
    void extractsPercent() {
        assertThat(CopilotIntent.extractPercent("reduce shopping by 15%"))
                .contains(new BigDecimal("15"));
    }

    @Test
    @DisplayName("extracts a rupee amount, including one written with separators")
    void extractsAmount() {
        assertThat(CopilotIntent.extractAmount("can I afford to save 5000 every month"))
                .contains(new BigDecimal("5000"));
        assertThat(CopilotIntent.extractAmount("save ₹12,500 a month"))
                .contains(new BigDecimal("12500"));
    }

    @Test
    @DisplayName("does not mistake a percentage for an amount")
    void percentIsNotAnAmount() {
        // "15%" must not be read as 15 rupees; the percentage is stripped first.
        assertThat(CopilotIntent.extractAmount("cut shopping by 15%")).isEmpty();
    }

    @Test
    @DisplayName("ignores small integers that are month counts, not amounts")
    void smallIntegersAreNotAmounts() {
        assertThat(CopilotIntent.extractAmount("summarise my last 3 months")).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" covers {1} month(s)")
    @CsvSource({
            "'summarise my last three months',3",
            "'my last 6 months',6",
            "'the past two months',2",
            "'how did I do this month',1"
    })
    @DisplayName("extracts a month count, defaulting to one")
    void extractsMonths(String question, int expected) {
        assertThat(CopilotIntent.extractMonths(question)).isEqualTo(expected);
    }

    @Test
    @DisplayName("clamps an absurd month count into range")
    void clampsMonths() {
        assertThat(CopilotIntent.extractMonths("my last 99 months")).isEqualTo(12);
    }
}
