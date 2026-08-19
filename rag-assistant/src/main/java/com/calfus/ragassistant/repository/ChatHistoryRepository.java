package com.calfus.ragassistant.repository;

import com.calfus.ragassistant.model.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, UUID> {

    /**
     * Spring Data builds this query automatically from the method name:
     * "WHERE session_id = ? AND user_id = ? ORDER BY created_at ASC".
     *
     * Checking BOTH sessionId and userId (not just sessionId) matters for
     * security: sessionId is just a random string the frontend makes up, so
     * without also checking userId, one user could type in (or guess)
     * someone else's sessionId and read their conversation.
     */
    List<ChatHistory> findBySessionIdAndUserIdOrderByCreatedAtAsc(String sessionId, UUID userId);
}
