package com.etg.whatsapp;

import jakarta.persistence.*;
import java.time.Instant;

/** Local mirror of a Twilio Content API template (WhatsApp/RCS approval tracked here). */
@Entity
@Table(name = "content_templates")
public class ContentTemplate {
  public static final String WHATSAPP = "whatsapp";
  public static final String PENDING = "pending";
  public static final String APPROVED = "approved";
  public static final String REJECTED = "rejected";

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false) private String templateKey;
  @Column(nullable = false) private String channel = WHATSAPP;
  @Column(nullable = false) private String contentSid;
  @Column(nullable = false) private String status = PENDING;
  @Column(nullable = false) private boolean active = true;
  @Column(nullable = false) private Instant updatedAt = Instant.now();

  protected ContentTemplate() {}

  public ContentTemplate(String templateKey, String channel, String contentSid) {
    this.templateKey = templateKey;
    this.channel = channel;
    this.contentSid = contentSid;
  }

  public Long getId() { return id; }
  public String getTemplateKey() { return templateKey; }
  public String getChannel() { return channel; }
  public String getContentSid() { return contentSid; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public boolean isActive() { return active; }
  public void touch(Instant now) { this.updatedAt = now; }
}
