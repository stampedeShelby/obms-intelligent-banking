package com.obms.repository;

import com.obms.domain.CopilotMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CopilotMessageRepository extends JpaRepository<CopilotMessage, Long> {

    List<CopilotMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    /** Recent turns used to give the copilot conversational context. */
    List<CopilotMessage> findTop10ByConversationIdOrderByCreatedAtDesc(Long conversationId);
}
