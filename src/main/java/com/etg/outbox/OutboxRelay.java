package com.etg.outbox;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes unpublished outbox rows to the {@code etg.events} topic exchange.
 * Fail-open: broker absence only delays delivery — rows stay unpublished for the next tick.
 */
@Component
public class OutboxRelay {
  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

  private final OutboxRepository outbox;
  private final RabbitTemplate rabbit;
  private final String exchange;

  public OutboxRelay(OutboxRepository outbox, RabbitTemplate rabbit,
                     @Value("${etg.events-exchange:etg.events}") String exchange) {
    this.outbox = outbox;
    this.rabbit = rabbit;
    this.exchange = exchange;
  }

  @Scheduled(fixedDelayString = "${etg.outbox-relay-delay-ms:5000}")
  @Transactional
  public void publishPending() {
    List<OutboxEvent> pending = outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
    for (OutboxEvent e : pending) {
      try {
        rabbit.convertAndSend(exchange, e.getEventType(), e.getPayload());
        e.markPublished();
        outbox.save(e);
      } catch (AmqpException ex) {
        log.warn("Outbox publish failed (event {}), will retry: {}", e.getId(), ex.getMessage());
        return; // keep order, retry from here next tick
      }
    }
  }
}
