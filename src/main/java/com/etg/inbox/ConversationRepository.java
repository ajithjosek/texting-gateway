package com.etg.inbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
  Optional<Conversation> findFirstByCustomerPhoneAndTopicAndStatusNot(
      String customerPhone, String topic, String status);
  List<Conversation> findByStatusNotOrderByUpdatedAtDesc(String status);
  List<Conversation> findByAssigneeAgentIdAndStatusNot(String agentId, String status);
  List<Conversation> findByStatusNotAndRepliedFalseAndFirstReplyDueAtBefore(
      String status, Instant now);
}
