package com.obms.service;

import com.obms.common.ApiException;
import com.obms.domain.Account;
import com.obms.domain.Customer;
import com.obms.domain.Enums;
import com.obms.dto.AccountDtos;
import com.obms.dto.AnalyticsDtos;
import com.obms.dto.DashboardDtos;
import com.obms.dto.HealthDtos;
import com.obms.dto.SavingsGoalDtos;
import com.obms.repository.AccountRepository;
import com.obms.repository.CustomerRepository;
import com.obms.repository.KycRepository;
import com.obms.service.fraud.FraudService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Assembles the customer dashboard (core banking module B).
 *
 * <p>Every number on this screen is derived from the authenticated customer's
 * own database records. There is no placeholder, sample figure or hardcoded
 * total anywhere in this service - if a section has nothing to show, it comes
 * back empty and the UI renders an empty state rather than a fake one.</p>
 *
 * <p>The health score is computed defensively: a failure in the scoring module
 * should leave the dashboard without a score, not without a dashboard.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int TOP_CATEGORY_LIMIT = 4;
    private static final int UPCOMING_BILL_DAYS = 14;

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final KycRepository kycRepository;
    private final AccountService accountService;
    private final AnalyticsService analyticsService;
    private final FinancialHealthService healthService;
    private final SavingsGoalService savingsGoalService;
    private final BillPaymentService billPaymentService;
    private final NotificationService notificationService;
    private final FraudService fraudService;

    @Transactional
    public DashboardDtos.Dashboard build(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Customer"));

        List<Account> accounts = accountRepository.findByCustomerIdOrderByCreatedAtAsc(customerId);
        BigDecimal totalBalance = accounts.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        AnalyticsDtos.SpendingAnalytics analytics = analyticsService.analyse(customerId, null);

        List<AnalyticsDtos.CategorySlice> topCategories = analytics.categories().stream()
                .limit(TOP_CATEGORY_LIMIT)
                .toList();

        Integer healthScore = null;
        String healthBand = null;
        try {
            HealthDtos.FinancialHealth health = healthService.evaluate(customerId, null);
            healthScore = health.score();
            healthBand = health.band();
        } catch (RuntimeException ex) {
            // The dashboard is still useful without a score; losing the whole
            // screen because one panel failed is the worse outcome.
            log.warn("Health score unavailable for customer {}", customerId, ex);
        }

        List<DashboardDtos.AlertTile> alerts = fraudService.activeAlertsFor(customerId).stream()
                .map(alert -> new DashboardDtos.AlertTile(
                        alert.getId(), alert.getRiskLevel(), alert.getAmount(),
                        alert.getPrimaryReason(), alert.getCreatedAt()))
                .toList();

        List<SavingsGoalDtos.GoalView> goals = savingsGoalService.list(customerId).stream()
                .filter(goal -> goal.status() == Enums.GoalStatus.ACTIVE)
                .toList();

        String kycStatus = kycRepository.findByCustomerId(customerId)
                .map(kyc -> kyc.getStatus().name())
                .orElse(Enums.KycStatus.NOT_SUBMITTED.name());

        List<AccountDtos.AccountSummary> accountCards = accounts.stream()
                .map(AccountDtos.AccountSummary::from)
                .toList();

        return new DashboardDtos.Dashboard(
                customer.fullName(),
                totalBalance,
                accountCards,
                accountService.recentAcrossAccounts(customerId),
                analytics.totalIncome(),
                analytics.totalSpend(),
                topCategories,
                healthScore,
                healthBand,
                alerts,
                goals,
                billPaymentService.upcoming(customerId, UPCOMING_BILL_DAYS),
                notificationService.unreadCount(customerId),
                kycStatus);
    }
}
