package com.etg.consent;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only audit row for every consent transition. Backs TCPA proof + eDiscovery. */
@Entity
@Table(name = "consent_events")
public class ConsentEvent {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false) private String phoneE164;
  @Column(nullable = false) private String topic;
  private String oldStatus;
  @Column(nullable = false) private String newStatus;
  @Column(nullable = false) private String source;
  private String actor;
  @Column(nullable = false) private Instant createdAt = Instant.now();

  protected ConsentEvent() {}

  public ConsentEvent(String phoneE164, String topic, String oldStatus,
                      String newStatus, String source, String actor) {
    this.phoneE164 = phoneE164;
    this.topic = topic;
    this.oldStatus = oldStatus;
    this.newStatus = newStatus;
    this.source = source;
    this.actor = actor;
  }

  public Long getId() { return id; }
  public String getPhoneE164() { return phoneE164; }
  public String getTopic() { return topic; }
  public String getOldStatus() { return oldStatus; }
  public String getNewStatus() { return newStatus; }
  public String getSource() { return source; }
}
