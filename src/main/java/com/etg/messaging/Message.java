package com.etg.messaging;

import jakarta.persistence.*;
import java.time.Instant;

/** Persisted outbound message. Body stored as SHA-256 hash only (PII minimization, ADR-007). */
@Entity
@Table(name = "messages")
public class Message {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false) private String tenant = "default";
  @Column(nullable = false) private String toPhone;
  private String fromSender;
  @Column(nullable = false) private String channel = "sms";
  @Column(nullable = false) private String topic;
  @Column(nullable = false) private String bodyHash;
  @Column(nullable = false) private String status = "queued";
  private String twilioSid;
  @Column(nullable = false, unique = true) private String idempotencyKey;
  @Column(nullable = false) private Instant createdAt = Instant.now();

  protected Message() {}

  public Message(String tenant, String toPhone, String fromSender, String channel,
                 String topic, String bodyHash, String status, String twilioSid, String idempotencyKey) {
    this.tenant = tenant;
    this.toPhone = toPhone;
    this.fromSender = fromSender;
    this.channel = channel;
    this.topic = topic;
    this.bodyHash = bodyHash;
    this.status = status;
    this.twilioSid = twilioSid;
    this.idempotencyKey = idempotencyKey;
  }

  public Long getId() { return id; }
  public String getTenant() { return tenant; }
  public String getToPhone() { return toPhone; }
  public String getChannel() { return channel; }
  public String getTopic() { return topic; }
  public String getBodyHash() { return bodyHash; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getTwilioSid() { return twilioSid; }
  public String getIdempotencyKey() { return idempotencyKey; }
}
