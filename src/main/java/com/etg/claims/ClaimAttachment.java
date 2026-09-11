package com.etg.claims;

import jakarta.persistence.*;
import java.time.Instant;

/** Stored inbound claim photo, optionally linked to a claim number. */
@Entity
@Table(name = "claim_attachments")
public class ClaimAttachment {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false) private String phoneE164;
  private String claimNumber;
  @Column(nullable = false) private String mediaRef;
  @Column(nullable = false) private String contentType;
  @Column(nullable = false) private long sizeBytes;
  @Column(nullable = false) private Instant createdAt = Instant.now();

  protected ClaimAttachment() {}

  public ClaimAttachment(String phoneE164, String claimNumber, String mediaRef,
                         String contentType, long sizeBytes) {
    this.phoneE164 = phoneE164;
    this.claimNumber = claimNumber;
    this.mediaRef = mediaRef;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
  }

  public Long getId() { return id; }
  public String getPhoneE164() { return phoneE164; }
  public String getClaimNumber() { return claimNumber; }
  public String getMediaRef() { return mediaRef; }
  public String getContentType() { return contentType; }
  public long getSizeBytes() { return sizeBytes; }
}
