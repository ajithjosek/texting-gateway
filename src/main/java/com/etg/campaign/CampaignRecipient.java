package com.etg.campaign;

import jakarta.persistence.*;
import java.time.Instant;

/** Snapshot of one campaign recipient at creation time (stable against later opt-outs). */
@Entity
@Table(name = "campaign_recipients")
public class CampaignRecipient {
  public static final String PENDING = "PENDING";
  public static final String SENT = "SENT";
  public static final String SKIPPED = "SKIPPED";

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false) private Long campaignId;
  @Column(nullable = false) private String phoneE164;
  @Column(nullable = false) private String status = PENDING;
  private String skipReason;
  @Column(nullable = false) private Instant createdAt = Instant.now();

  protected CampaignRecipient() {}

  public CampaignRecipient(Long campaignId, String phoneE164) {
    this.campaignId = campaignId;
    this.phoneE164 = phoneE164;
  }

  public Long getId() { return id; }
  public Long getCampaignId() { return campaignId; }
  public String getPhoneE164() { return phoneE164; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getSkipReason() { return skipReason; }
  public void setSkipReason(String skipReason) { this.skipReason = skipReason; }
}
