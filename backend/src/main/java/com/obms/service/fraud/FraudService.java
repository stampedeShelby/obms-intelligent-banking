package com.obms.service.fraud;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obms.common.ApiException;
import com.obms.config.AppProperties;
import com.obms.domain.Account;
import com.obms.domain.Beneficiary;
import com.obms.domain.Customer;
import com.obms.domain.Enums;
import com.obms.domain.FraudAlert;
import com.obms.domain.Transaction;
import com.obms.repository.FraudAlertRepository;
import com.obms.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Assembles behavioural features, obtains a risk assessment, and records it.
 *
 * <p>This service is the join between the banking domain and the model. It does
 * three things and nothing else: compute features from the customer's own
 * history, ask the model for a score, and persist an explainable audit row.
 * The transfer workflow decides what to do with the answer.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final int HISTORY_WINDOW = 100;

    private final FraudScoringClient scoringClient;
    private final FraudAlertRepository fraudAlertRepository;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    /**
     * Scores a proposed debit from an account.
     *
     * @param source      account the money would leave
     * @param amount      proposed amount
     * @param beneficiary payee, null for a bill payment or own-account transfer
     * @param category    spend category of the proposed transaction
     * @return the assessment; never null, degraded if the model is unreachable
     */
    @Transactional(readOnly = true)
    public RiskAssessment assess(Account source, BigDecimal amount,
                                 Beneficiary beneficiary, Enums.SpendCategory category) {
        Long customerId = source.getCustomer().getId();
        List<Transaction> history = transactionRepository.findDebitHistory(
                customerId, PageRequest.of(0, HISTORY_WINDOW));

        DebitStats stats = DebitStats.from(history);
        Instant now = Instant.now();

        long recent1h = transactionRepository.countRecentDebits(customerId, now.minus(1, ChronoUnit.HOURS));
        long recent24h = transactionRepository.countRecentDebits(customerId, now.minus(24, ChronoUnit.HOURS));

        long beneficiaryAgeHours = beneficiary == null || beneficiary.getVerifiedAt() == null
                ? -1
                : ChronoUnit.HOURS.between(beneficiary.getVerifiedAt(), now);
        boolean newBeneficiary = beneficiaryAgeHours >= 0 && beneficiaryAgeHours < 24;

        BigDecimal available = source.availableBalance();
        BigDecimal balanceRatio = available.signum() > 0
                ? amount.divide(available, 4, RoundingMode.HALF_UP)
                : BigDecimal.ONE;

        FraudScoringClient.ScoringRequest request = new FraudScoringClient.ScoringRequest(
                amount,
                stats.mean(),
                stats.stdDev(),
                stats.max(),
                stats.count(),
                LocalDateTime.ofInstant(now, ZONE).getHour(),
                newBeneficiary,
                Math.max(beneficiaryAgeHours, 0),
                recent1h,
                recent24h,
                countDistinctPayees(history, now.minus(7, ChronoUnit.DAYS)),
                balanceRatio,
                category == null ? Enums.SpendCategory.OTHER.name() : category.name());

        return scoringClient.score(request);
    }

    /**
     * Persists the assessment as an audit row.
     *
     * <p>Runs in its own transaction so the record survives even when the
     * transfer it describes is subsequently rolled back - a blocked attempt is
     * precisely the thing an investigator needs to see.</p>
     *
     * @param transaction the ledger row, null when the attempt never reached the ledger
     * @return the persisted alert
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FraudAlert recordAssessment(Customer customer, Transaction transaction, String evaluatedRef,
                                       BigDecimal amount, RiskAssessment assessment) {
        FraudAlert alert = new FraudAlert();
        alert.setCustomer(customer);
        alert.setTransaction(transaction);
        alert.setEvaluatedRef(evaluatedRef);
        alert.setAmount(amount);
        alert.setRiskScore(assessment.score());
        alert.setRiskLevel(assessment.level());
        alert.setPrimaryReason(assessment.primaryReason());
        alert.setDecision(assessment.decision().name());
        alert.setModelVersion(assessment.modelVersion());
        alert.setExplanationJson(serialiseFactors(assessment));
        alert.setStatus(assessment.level() == Enums.RiskLevel.LOW
                ? Enums.AlertStatus.CLOSED
                : Enums.AlertStatus.OPEN);
        return fraudAlertRepository.save(alert);
    }

    private String serialiseFactors(RiskAssessment assessment) {
        try {
            return objectMapper.writeValueAsString(assessment.factors());
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialise risk factors", ex);
            return "[]";
        }
    }

    // ---- customer-facing reads -------------------------------------------

    @Transactional(readOnly = true)
    public List<FraudAlert> activeAlertsFor(Long customerId) {
        return fraudAlertRepository.findActiveForCustomer(customerId);
    }

    @Transactional(readOnly = true)
    public Page<FraudAlert> alertsFor(Long customerId, Pageable pageable) {
        return fraudAlertRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
    }

    /**
     * Records that an alert was a false positive.
     *
     * <p>Collected as explicit model feedback. The flag is stored rather than
     * used to retrain automatically - a customer dismissing an alert is a
     * signal, not a licence to weaken the control.</p>
     */
    @Transactional
    public FraudAlert markFalsePositive(Long alertId, Long customerId, String note) {
        FraudAlert alert = fraudAlertRepository.findByIdAndCustomerId(alertId, customerId)
                .orElseThrow(() -> ApiException.notFound("Alert"));
        alert.setMarkedFalsePositive(true);
        alert.setStatus(Enums.AlertStatus.FALSE_POSITIVE);
        alert.setReviewNotes(note);
        alert.setReviewedAt(Instant.now());
        return alert;
    }

    // ---- analyst-facing reads --------------------------------------------

    @Transactional(readOnly = true)
    public Page<FraudAlert> queue(Enums.AlertStatus status, Pageable pageable) {
        return fraudAlertRepository.findByStatusOrderByRiskScoreDesc(status, pageable);
    }

    @Transactional
    public FraudAlert review(Long alertId, Enums.AlertStatus newStatus, String analyst, String notes) {
        FraudAlert alert = fraudAlertRepository.findById(alertId)
                .orElseThrow(() -> ApiException.notFound("Alert"));
        alert.setStatus(newStatus);
        alert.setReviewedBy(analyst);
        alert.setReviewedAt(Instant.now());
        alert.setReviewNotes(notes);
        alert.setMarkedFalsePositive(newStatus == Enums.AlertStatus.FALSE_POSITIVE);
        return alert;
    }

    @Transactional(readOnly = true)
    public MonitoringSummary monitoringSummary(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return new MonitoringSummary(
                transactionRepository.countSince(since),
                fraudAlertRepository.countSince(since),
                fraudAlertRepository.countSuspiciousSince(since),
                fraudAlertRepository.countByRiskLevel(Enums.RiskLevel.LOW),
                fraudAlertRepository.countByRiskLevel(Enums.RiskLevel.MEDIUM),
                fraudAlertRepository.countByRiskLevel(Enums.RiskLevel.HIGH),
                fraudAlertRepository.countByRiskLevel(Enums.RiskLevel.CRITICAL),
                fraudAlertRepository.countByStatus(Enums.AlertStatus.OPEN),
                fraudAlertRepository.countByStatus(Enums.AlertStatus.FALSE_POSITIVE));
    }

    private long countDistinctPayees(List<Transaction> history, Instant since) {
        Set<String> payees = new HashSet<>();
        for (Transaction txn : history) {
            if (txn.getBookedAt() != null && txn.getBookedAt().isAfter(since)
                    && txn.getCounterpartyName() != null) {
                payees.add(txn.getCounterpartyName());
            }
        }
        return payees.size();
    }

    /**
     * Summary figures for the fraud monitoring dashboard.
     */
    public record MonitoringSummary(
            long transactionsAnalysed,
            long alertsRaised,
            long suspiciousCount,
            long lowCount,
            long mediumCount,
            long highCount,
            long criticalCount,
            long openInvestigations,
            long falsePositives
    ) {
    }

    /**
     * Descriptive statistics over a customer's debit history.
     *
     * <p>Computed in Java rather than fetched as SQL aggregates because the
     * same numbers feed the copilot and the analytics module, and because a
     * population standard deviation over at most 100 rows is cheap.</p>
     */
    record DebitStats(BigDecimal mean, BigDecimal stdDev, BigDecimal max, long count) {

        static DebitStats from(List<Transaction> debits) {
            if (debits.isEmpty()) {
                return new DebitStats(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
            }
            BigDecimal total = BigDecimal.ZERO;
            BigDecimal max = BigDecimal.ZERO;
            for (Transaction txn : debits) {
                total = total.add(txn.getAmount());
                if (txn.getAmount().compareTo(max) > 0) {
                    max = txn.getAmount();
                }
            }
            int n = debits.size();
            BigDecimal mean = total.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);

            BigDecimal varianceSum = BigDecimal.ZERO;
            for (Transaction txn : debits) {
                BigDecimal diff = txn.getAmount().subtract(mean);
                varianceSum = varianceSum.add(diff.multiply(diff));
            }
            BigDecimal variance = varianceSum.divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
            BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()))
                    .setScale(2, RoundingMode.HALF_UP);

            return new DebitStats(mean, stdDev, max, n);
        }
    }
}
