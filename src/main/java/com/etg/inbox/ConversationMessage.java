package com.etg.inbox;

import jakarta.persistence.*;
import java.time.Instant;

/** One entry in a conversation thread. INTERNAL entries never leave the inbox. */
@Entity
@Table(name = "conversation_messages")
public class ConversationMessage {
  public static final String IN = "IN";
  public static final String OUT = "OUT";
  public static final String CUSTOMER = "CUSTOMER";
  public static final String INTERNAL = "INTERNAL";

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false) private Long conversationId;
  @Column(nullable = false) private String direction;
  @Column(nullable = false) private String visibility = CUSTOMER;
  private String senderAgentId;
  @Column(nullable = false, columnDefinition = "TEXT") private String body;
  @Column(nullable = false) private Instant createdAt = Instant.now();

  protected ConversationMessage() {}

  public ConversationMessage(Long conversationId, String direction, String visibility,
                             String senderAgentId, String body) {
    this.conversationId = conversationId;
    this.direction = direction;
    this.visibility = visibility;
    this.senderAgentId = senderAgentId;
    this.body = body;
  }

  public Long getId() { return id; }
  public Long getConversationId() { return conversationId; }
  public String getDirection() { return direction; }
  public String getVisibility() { return visibility; }
  public String getSenderAgentId() { return senderAgentId; }
  public String getBody() { return body; }
  public Instant getCreatedAt() { return createdAt; }
}
