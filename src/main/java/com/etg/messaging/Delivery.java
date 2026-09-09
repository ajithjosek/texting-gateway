package com.etg.messaging;

import jakarta.persistence.*;
import java.time.Instant;

/** One row per Twilio delivery receipt. Append-only. */
@Entity
@Table(name = "deliveries")
public class Delivery {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false) private String messageSid;
  @Column(nullable = false) private String status;
  private String errorCode;
  @Column(nullable = false) private Instant createdAt = Instant.now();

  protected Delivery() {}

  public Delivery(String messageSid, String status, String errorCode) {
    this.messageSid = messageSid;
    this.status = status;
    this.errorCode = errorCode;
  }

  public Long getId() { return id; }
  public String getMessageSid() { return messageSid; }
  public String getStatus() { return status; }
  public String getErrorCode() { return errorCode; }
}
