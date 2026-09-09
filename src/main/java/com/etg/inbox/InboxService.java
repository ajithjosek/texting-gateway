package com.etg.inbox;

import com.etg.consent.ConsentService;
import com.etg.messaging.MessageService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Agent inbox: one open thread per (phone, topic). Replies flow through the
 * governed send pipeline (consent + idempotency + outbox); internal notes never leave.
 */
@Service
public class InboxService {
  static final Duration FIRST_REPLY_SLA = Duration.ofMinutes(5);
  static final Duration COLLISION_WINDOW = Duration.ofMinutes(15);

  private final ConversationRepository conversations;
  private final ConversationMessageRepository thread;
  private final ConsentService consent;
  private final MessageService messages;
  private final Clock clock;

  public InboxService(ConversationRepository conversations, ConversationMessageRepository thread,
                      ConsentService consent, MessageService messages, Clock clock) {
    this.conversations = conversations;
    this.thread = thread;
    this.consent = consent;
    this.messages = messages;
    this.clock = clock;
  }

  @Transactional
  public Conversation noteInbound(String phone, String topic, String body) {
    Conversation c = openOrNew(phone, topic);
    thread.save(new ConversationMessage(c.getId(), ConversationMessage.IN,
        ConversationMessage.CUSTOMER, null, body));
    c.touch(now());
    return conversations.save(c);
  }

  @Transactional
  public Conversation assign(Long id, String agentId) {
    Conversation c = open(id);
    if (Conversation.ASSIGNED.equals(c.getStatus())
        && c.getAssigneeAgentId() != null
        && !c.getAssigneeAgentId().equals(agentId)
        && c.getUpdatedAt().isAfter(now().minus(COLLISION_WINDOW))) {
      throw new CollisionException("INBOX_COLLISION: " + c.getAssigneeAgentId() + " is working this thread");
    }
    c.setStatus(Conversation.ASSIGNED);
    c.setAssigneeAgentId(agentId);
    c.touch(now());
    return conversations.save(c);
  }

  @Transactional
  public Conversation reply(Long id, String agentId, String body) {
    Conversation c = open(id);
    consent.requireOptIn(c.getCustomerPhone(), c.getTopic());
    if (c.getAssigneeAgentId() == null) {
      c.setStatus(Conversation.ASSIGNED);
      c.setAssigneeAgentId(agentId);
    }
    messages.send(c.getCustomerPhone(), c.getTopic(), body,
        "inbox:" + c.getId() + ":" + UUID.randomUUID(), null);
    thread.save(new ConversationMessage(c.getId(), ConversationMessage.OUT,
        ConversationMessage.CUSTOMER, agentId, body));
    c.setReplied(true);
    c.touch(now());
    return conversations.save(c);
  }

  @Transactional
  public ConversationMessage note(Long id, String agentId, String body) {
    Conversation c = open(id);
    ConversationMessage m = thread.save(new ConversationMessage(c.getId(),
        ConversationMessage.OUT, ConversationMessage.INTERNAL, agentId, body));
    c.touch(now());
    conversations.save(c);
    return m;
  }

  @Transactional
  public Conversation close(Long id) {
    Conversation c = open(id);
    c.setStatus(Conversation.CLOSED);
    c.setClosedAt(now());
    c.touch(now());
    return conversations.save(c);
  }

  @Transactional
  public Conversation csat(Long id, int score) {
    if (score < 1 || score > 5) throw new IllegalArgumentException("CSAT score must be 1-5");
    Conversation c = conversations.findById(id)
        .orElseThrow(() -> new NotFoundException("INBOX_NOT_FOUND: " + id));
    c.setCsatScore(score);
    c.touch(now());
    return conversations.save(c);
  }

  public List<Conversation> queue(String status, String assignee) {
    if (assignee != null) return conversations.findByAssigneeAgentIdAndStatusNot(assignee, Conversation.CLOSED);
    if (status != null) return conversations.findByStatusNotOrderByUpdatedAtDesc(Conversation.CLOSED)
        .stream().filter(c -> status.equalsIgnoreCase(c.getStatus())).toList();
    return conversations.findByStatusNotOrderByUpdatedAtDesc(Conversation.CLOSED);
  }

  public List<Conversation> breached() {
    return conversations.findByStatusNotAndRepliedFalseAndFirstReplyDueAtBefore(
        Conversation.CLOSED, now());
  }

  public List<ConversationMessage> transcript(Long id) {
    conversations.findById(id).orElseThrow(() -> new NotFoundException("INBOX_NOT_FOUND: " + id));
    return thread.findByConversationIdOrderByCreatedAtAscIdAsc(id);
  }

  private Conversation openOrNew(String phone, String topic) {
    String t = topic == null ? "servicing" : topic;
    return conversations.findFirstByCustomerPhoneAndTopicAndStatusNot(phone, t, Conversation.CLOSED)
        .orElseGet(() -> conversations.save(
            new Conversation(phone, t, now().plus(FIRST_REPLY_SLA))));
  }

  private Conversation open(Long id) {
    Conversation c = conversations.findById(id)
        .orElseThrow(() -> new NotFoundException("INBOX_NOT_FOUND: " + id));
    if (Conversation.CLOSED.equals(c.getStatus())) {
      throw new ClosedException("INBOX_CLOSED: " + id);
    }
    return c;
  }

  private Instant now() { return Instant.now(clock); }

  @ResponseStatus(HttpStatus.CONFLICT)
  public static class CollisionException extends RuntimeException {
    public CollisionException(String message) { super(message); }
  }

  @ResponseStatus(HttpStatus.CONFLICT)
  public static class ClosedException extends RuntimeException {
    public ClosedException(String message) { super(message); }
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  public static class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
  }
}
