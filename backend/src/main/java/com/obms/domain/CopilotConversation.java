package com.obms.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A chat thread with the AI Financial Copilot (innovation module 1).
 *
 * <p>Conversations are scoped to a customer so that the copilot can hold
 * context across turns while remaining incapable of reaching another
 * customer's data: every data lookup it performs is filtered by this
 * customer id, not by anything the model produced.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "copilot_conversations", indexes = {
        @Index(name = "idx_convo_customer", columnList = "customer_id, created_at")
})
public class CopilotConversation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(length = 160)
    private String title;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<CopilotMessage> messages = new ArrayList<>();
}
