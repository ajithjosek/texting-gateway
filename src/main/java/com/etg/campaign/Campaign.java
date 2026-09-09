package com.etg.campaign;

import jakarta.persistence.*;
import java.time.Instant;

/** Bulk campaign lifecycle: DRAFT -> PENDING -> APPROVED -> RUNNING -> DONE (or REJECTED). */
@Entity
@Table(name = "campaigns")
public class Campaign {
  public static final String DRAFT = "DRAFT";
  public static final String PENDING = "PENDING";
  public static final String APPROVED = "APPROVED";
  public static final String REJECTED = "REJECTED";
  public static final String RUNNING = "RUNNING";
  public static final String DONE = "DONE";

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false) private String tenant = "default";
  @Column(nullable = false) private String name;
  @Column(nullable = false) private String topic = "marketing";
  @Column(nullable = false) private String templateKey;
  @Column(nullable = false) private String locale = "en";
  @Column(columnDefinition = "TEXT") private String varsJson = "{}";
  @Column(nullable = false) private String status = DRAFT;
  private String createdBy;
  private String approvedBy;
  @Column(nullable = false) private int sentCount;
  @Column(nullable = false) private int skippedCount;
  @Column(nullable = false) private Instant createdAt = Instant.now();
  @Column(nullable = false) private Instant updatedAt = Instant.now();

  protected Campaign() {}

  public Campaign(String name, String templateKey, String locale, String varsJson, String createdBy) {
    this.name = name;
    this.templateKey = templateKey;
    this.locale = locale == null ? "en" : locale;
    this.varsJson = varsJson == null ? "{}" : varsJson;
    this.createdBy = createdBy;
  }

  public Long getId() { return id; }
  public String getName() { return name; }
  public String getTopic() { return topic; }
  public String getTemplateKey() { return templateKey; }
  public String getLocale() { return locale; }
  public String getVarsJson() { return varsJson; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getCreatedBy() { return createdBy; }
  public String getApprovedBy() { return approvedBy; }
  public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
  public int getSentCount() { return sentCount; }
  public void setSentCount(int sentCount) { this.sentCount = sentCount; }
  public int getSkippedCount() { return skippedCount; }
  public void setSkippedCount(int skippedCount) { this.skippedCount = skippedCount; }
  public void touch(Instant now) { this.updatedAt = now; }
}
