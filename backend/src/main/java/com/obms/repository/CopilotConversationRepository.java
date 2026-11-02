package com.obms.repository;

import com.obms.domain.CopilotConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CopilotConversationRepository extends JpaRepository<CopilotConversation, Long> {

    List<CopilotConversation> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    /**
     * Scoping the lookup by customer id, rather than by conversation id alone,
     * is what prevents one customer from reading another's copilot thread.
     */
    Optional<CopilotConversation> findByIdAndCustomerId(Long id, Long customerId);
}
