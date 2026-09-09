package com.etg.outbox;

import jakarta.persistence.*;
import java.time.Instant;

/** Transactional outbox: domain events committed atomically with business writes, relayed to RabbitMQ. */
@Entity
@Table(name = "outbox")
public class OutboxEvent {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false) private String aggregateType;
  @Column(nullable = false) private String aggregateId;
  @Column(nullable = false) private String eventType;
  @Column(nullable = false, columnDefinition = "TEXT") private String payload;
  @Column(nullable = false) private Instant createdAt = Instant.now();
  private Instant publishedAt;

  protected OutboxEvent() {}

  public OutboxEvent(String aggregateType, String aggregateId, String eventType, String payload) {
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
  }

  public Long getId() { return id; }
  public String getAggregateType() { return aggregateType; }
  public String getAggregateId() { return aggregateId; }
  public String getEventType() { return eventType; }
  public String getPayload() { return payload; }
  public Instant getPublishedAt() { return publishedAt; }
  public void markPublished() { this.publishedAt = Instant.now(); }
}
