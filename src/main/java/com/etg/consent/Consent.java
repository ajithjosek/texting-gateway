package com.etg.consent;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "consents", uniqueConstraints = @UniqueConstraint(columnNames = {"phoneE164", "topic"}))
public class Consent {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false) private String phoneE164;
  @Column(nullable = false) private String topic; // servicing|billing|claims|marketing
  @Column(nullable = false) private String status; // opted_in|opted_out
  @Column(nullable = false) private String source; // keyword|web|agent|api
  @Column(nullable = false) private String proofTextVersion;
  private String actor;
  @Column(nullable = false) private Instant updatedAt = Instant.now();

  protected Consent() {}
  public Consent(String phoneE164, String topic, String status, String source, String proofTextVersion, String actor) {
    this.phoneE164 = phoneE164; this.topic = topic; this.status = status;
    this.source = source; this.proofTextVersion = proofTextVersion; this.actor = actor;
  }
  public String getStatus() { return status; }
  public void setStatus(String s) { this.status = s; this.updatedAt = Instant.now(); }
  public String getPhoneE164() { return phoneE164; }
  public String getTopic() { return topic; }
}
