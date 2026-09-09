package com.etg.inbox;

import jakarta.persistence.*;
import java.time.Instant;

/** Agent inbox conversation: one open thread per (phone, topic). */
@Entity
@Table(name = "conversations")
public class Conversation {
  public static final String OPEN = "OPEN";
  public static final String ASSIGNED = "ASSIGNED";
  public static final String CLOSED = "CLOSED";

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false) private String tenant = "default";
  @Column(nullable = false) private String customerPhone;
  @Column(nullable = false) private String topic = "servicing";
  private String refId;
  @Column(nullable = false) private String status = OPEN;
  private String assigneeAgentId;
  @Column(nullable = false) private String channel = "sms";
  @Column(nullable = false) private boolean replied;
  @Column(nullable = false) private Instant firstReplyDueAt;
  @Column(nullable = false) private Instant createdAt = Instant.now();
  @Column(nullable = false) private Instant updatedAt = Instant.now();
  private Instant closedAt;
  private Integer csatScore;

  protected Conversation() {}

  public Conversation(String customerPhone, String topic, Instant firstReplyDueAt) {
    this.customerPhone = customerPhone;
    this.topic = topic;
    this.firstReplyDueAt = firstReplyDueAt;
  }

  public Long getId() { return id; }
  public String getCustomerPhone() { return customerPhone; }
  public String getTopic() { return topic; }
  public String getRefId() { return refId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getAssigneeAgentId() { return assigneeAgentId; }
  public void setAssigneeAgentId(String assigneeAgentId) { this.assigneeAgentId = assigneeAgentId; }
  public boolean isReplied() { return replied; }
  public void setReplied(boolean replied) { this.replied = replied; }
  public Instant getFirstReplyDueAt() { return firstReplyDueAt; }
  public void setFirstReplyDueAt(Instant firstReplyDueAt) { this.firstReplyDueAt = firstReplyDueAt; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void touch(Instant now) { this.updatedAt = now; }
  public Instant getClosedAt() { return closedAt; }
  public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
  public Integer getCsatScore() { return csatScore; }
  public void setCsatScore(Integer csatScore) { this.csatScore = csatScore; }
}
