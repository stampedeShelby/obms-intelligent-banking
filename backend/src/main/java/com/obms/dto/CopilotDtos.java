package com.obms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** AI Financial Copilot request and response shapes (innovation module 1). */
public final class CopilotDtos {

    private CopilotDtos() {
        // DTO holder
    }

    public record AskRequest(
            @NotBlank(message = "Type a question")
            @Size(max = 500, message = "Keep questions under 500 characters")
            String question,

            /** Continue an existing thread, or null to start one. */
            Long conversationId
    ) {
    }

    /**
     * A chart the UI can render beneath the answer.
     *
     * @param type   {@code bar}, {@code line} or {@code pie}
     * @param title  chart heading
     * @param labels category or period labels
     * @param values corresponding numeric values
     */
    public record ChartData(String type, String title, List<String> labels, List<Double> values) {
    }

    /**
     * The copilot's reply.
     *
     * <p>{@code generator} is reported honestly: {@code rule_engine} when the
     * answer came from deterministic analytics, {@code llm} when a language
     * model phrased it. The figures are computed in Java either way - the model
     * never invents a number.</p>
     *
     * @param conversationId thread this turn belongs to
     * @param answer         the prose answer
     * @param intent         detected intent, or {@code UNKNOWN}
     * @param figures        the supporting numbers the answer rests on
     * @param chart          optional chart data
     * @param generator      {@code rule_engine} or {@code llm}
     * @param dataAvailable  false when there was no data to answer from
     * @param askedAt        when the turn was answered
     */
    public record AskResponse(
            Long conversationId,
            String answer,
            String intent,
            Map<String, String> figures,
            ChartData chart,
            String generator,
            boolean dataAvailable,
            Instant askedAt
    ) {
    }

    public record ConversationSummary(Long id, String title, Instant createdAt, int messageCount) {
    }

    public record MessageView(String role, String content, String intent, Instant at) {
    }
}
