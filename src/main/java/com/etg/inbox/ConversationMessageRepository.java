package com.etg.inbox;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {
  List<ConversationMessage> findByConversationIdOrderByCreatedAtAscIdAsc(Long conversationId);
}
