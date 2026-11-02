package com.obms.service.fraud;

import com.obms.config.AppProperties;
import com.obms.domain.Enums;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for the Python fraud scoring service.
 *
 * <p>The banking backend owns the <em>decision</em>; this client only fetches a
 * score and an explanation. That separation is why the Python service can be
 * restarted, retrained or taken offline without the transfer path losing its
 * safety properties - the degraded fallback lives on the Java side.</p>
 */
@Slf4j
@Component
public class FraudScoringClient {

    private final RestTemplate restTemplate;
    private final AppProperties properties;

    public FraudScoringClient(RestTemplateBuilder builder, AppProperties properties) {
        this.properties = properties;
        int timeout = properties.getFraud().getTimeoutMs();
        this.restTemplate = builder
                .rootUri(properties.getFraud().getServiceUrl())
                .connectTimeout(Duration.ofMillis(timeout))
                .readTimeout(Duration.ofMillis(timeout))
                .build();
    }

    /**
     * Scores a proposed transaction.
     *
     * @param request behavioural features assembled by {@link FraudService}
     * @return the model's assessment, or a degraded fallback when the service
     *         cannot be reached within the timeout
     */
    public RiskAssessment score(ScoringRequest request) {
        if (!properties.getFraud().isEnabled()) {
            return RiskAssessment.degradedFallback(
                    request.amount(), properties.getTransfer().getAlwaysVerifyAbove());
        }
        try {
            Map<String, Object> body = restTemplate.postForObject(
                    "/api/v1/fraud/score", request, Map.class);
            if (body == null) {
                throw new RestClientException("Empty response from scoring service");
            }
            return mapResponse(body);
        } catch (RestClientException | ClassCastException ex) {
            log.warn("Fraud scoring unavailable ({}), applying degraded fallback",
                    ex.getClass().getSimpleName());
            return RiskAssessment.degradedFallback(
                    request.amount(), properties.getTransfer().getAlwaysVerifyAbove());
        }
    }

    @SuppressWarnings("unchecked")
    private RiskAssessment mapResponse(Map<String, Object> body) {
        int score = clampScore(toInt(body.get("risk_score")));
        Enums.RiskLevel level = bandFor(score);
        String reason = body.get("primary_reason") instanceof String s
                ? s : "Transaction scored by the anomaly detection model.";
        String modelVersion = body.get("model_version") instanceof String s ? s : "unknown";

        List<RiskAssessment.RiskFactor> factors = new ArrayList<>();
        if (body.get("factors") instanceof List<?> raw) {
            for (Object item : raw) {
                if (item instanceof Map<?, ?> map) {
                    factors.add(new RiskAssessment.RiskFactor(
                            String.valueOf(map.get("feature")),
                            String.valueOf(map.get("value")),
                            toInt(map.get("contribution")),
                            String.valueOf(map.get("explanation"))));
                }
            }
        }
        return new RiskAssessment(score, level, decisionFor(level), reason, factors, modelVersion, false);
    }

    /**
     * Maps a score to a band using the configured thresholds.
     *
     * <p>Kept on the Java side so the bank can retune its risk appetite
     * without redeploying the model.</p>
     */
    public Enums.RiskLevel bandFor(int score) {
        AppProperties.Fraud fraud = properties.getFraud();
        if (score >= fraud.getCriticalThreshold()) {
            return Enums.RiskLevel.CRITICAL;
        }
        if (score >= fraud.getHighThreshold()) {
            return Enums.RiskLevel.HIGH;
        }
        if (score >= fraud.getMediumThreshold()) {
            return Enums.RiskLevel.MEDIUM;
        }
        return Enums.RiskLevel.LOW;
    }

    /** The risk-based authentication policy, in one readable place. */
    public RiskAssessment.Decision decisionFor(Enums.RiskLevel level) {
        return switch (level) {
            case LOW -> RiskAssessment.Decision.ALLOW;
            case MEDIUM -> RiskAssessment.Decision.STEP_UP;
            case HIGH -> RiskAssessment.Decision.HOLD;
            case CRITICAL -> RiskAssessment.Decision.BLOCK;
        };
    }

    private static int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private static int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    /**
     * Features sent to the model. Deliberately contains no personally
     * identifying data - no names, no account numbers, no email addresses.
     * The model needs behaviour, not identity.
     *
     * @param amount                 proposed transfer amount
     * @param customerMeanDebit       mean of the customer's historical debits
     * @param customerStdDebit        standard deviation of those debits
     * @param customerMaxDebit        largest historical debit
     * @param historyCount            how many historical debits informed the stats
     * @param hourOfDay               0-23 local hour of the attempt
     * @param isNewBeneficiary        true when the payee was added recently
     * @param beneficiaryAgeHours     hours since the payee was verified
     * @param recentDebitCount1h      debits in the last hour
     * @param recentDebitCount24h     debits in the last day
     * @param distinctPayees7d        distinct payees paid in the last week
     * @param balanceRatio            amount as a fraction of available balance
     * @param category                spend category of the transaction
     */
    public record ScoringRequest(
            BigDecimal amount,
            BigDecimal customerMeanDebit,
            BigDecimal customerStdDebit,
            BigDecimal customerMaxDebit,
            long historyCount,
            int hourOfDay,
            boolean isNewBeneficiary,
            long beneficiaryAgeHours,
            long recentDebitCount1h,
            long recentDebitCount24h,
            long distinctPayees7d,
            BigDecimal balanceRatio,
            String category
    ) {
    }
}
