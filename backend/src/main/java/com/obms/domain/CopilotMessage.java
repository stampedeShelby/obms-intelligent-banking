package com.obms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One turn in a copilot conversation.
 *
 * <p>{@code dataJson} carries the figures the answer was computed from, so the
 * UI can render the supporting chart or table alongside the prose and the
 * customer can see the arithmetic rather than trusting the sentence. When the
 * answer came from the rule-based engine instead of an LLM, {@code generator}
 * records that - the system does not present deterministic analytics as model
 * output.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "copilot_messages")
public class CopilotMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private CopilotConversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Enums.MessageRole role;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Detected intent for an assistant reply, e.g. {@code CATEGORY_SPEND}. */
    @Column(length = 40)
    private String intent;

    /** Structured figures backing the answer, as JSON. */
    @Lob
    @Column(name = "data_json", columnDefinition = "TEXT")
    private String dataJson;

    /** {@code rule_engine} or {@code llm}. Never misreported. */
    @Column(length = 20)
    private String generator;
}
