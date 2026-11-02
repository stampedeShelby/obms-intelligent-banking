package com.obms.dto;

import com.obms.domain.Enums;

import java.math.BigDecimal;
import java.util.List;

/** Customer dashboard payload (core banking module B). */
public final class DashboardDtos {

    private DashboardDtos() {
        // DTO holder
    }

    /**
     * A fraud alert as the customer sees it.
     *
     * @param reason the explanation, never a bare score
     */
    public record AlertTile(
            Long id,
            Enums.RiskLevel riskLevel,
            BigDecimal amount,
            String reason,
            java.time.Instant raisedAt
    ) {
    }

    /**
     * Everything the dashboard needs, in one call.
     *
     * <p>Assembled server-side as a single payload rather than left to the SPA
     * to stitch from eight requests. Each figure is derived from the
     * authenticated customer's own records; nothing here is a placeholder.</p>
     *
     * @param displayName          customer's name for the greeting
     * @param totalBalance         sum across all their accounts
     * @param accounts             their account cards
     * @param recentTransactions   latest activity across all accounts
     * @param monthIncome          credits received this month
     * @param monthExpense         spending this month
     * @param topCategories        up to four largest spending categories
     * @param healthScore          current wellness score
     * @param healthBand           band that score falls in
     * @param activeAlerts         open fraud alerts worth the customer's attention
     * @param goals                active savings goals with progress
     * @param upcomingBills        unpaid bills due soon
     * @param unreadNotifications  count for the notification badge
     * @param kycStatus            whether transfers are unlocked
     */
    public record Dashboard(
            String displayName,
            BigDecimal totalBalance,
            List<AccountDtos.AccountSummary> accounts,
            List<AccountDtos.TransactionView> recentTransactions,
            BigDecimal monthIncome,
            BigDecimal monthExpense,
            List<AnalyticsDtos.CategorySlice> topCategories,
            Integer healthScore,
            String healthBand,
            List<AlertTile> activeAlerts,
            List<SavingsGoalDtos.GoalView> goals,
            List<BillPaymentDtos.PaymentHistoryItem> upcomingBills,
            long unreadNotifications,
            String kycStatus
    ) {
    }
}
