package com.etg.template;

import jakarta.persistence.*;
import java.time.Instant;

/** Versioned message template. Exactly one active version per (key, locale). */
@Entity
@Table(name = "templates", uniqueConstraints = @UniqueConstraint(columnNames = {"templateKey", "locale", "version"}))
public class MessageTemplate {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false) private String templateKey;
  @Column(nullable = false) private String locale;
  @Column(nullable = false) private int version;
  @Column(nullable = false, columnDefinition = "TEXT") private String body;
  @Column(nullable = false) private boolean active;
  @Column(nullable = false) private Instant createdAt = Instant.now();

  protected MessageTemplate() {}

  public MessageTemplate(String templateKey, String locale, int version, String body, boolean active) {
    this.templateKey = templateKey;
    this.locale = locale;
    this.version = version;
    this.body = body;
    this.active = active;
  }

  public Long getId() { return id; }
  public String getTemplateKey() { return templateKey; }
  public String getLocale() { return locale; }
  public int getVersion() { return version; }
  public String getBody() { return body; }
  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }
}
