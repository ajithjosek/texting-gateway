package com.etg.messaging;

import com.etg.outbox.OutboxEvent;
import com.etg.outbox.OutboxRepository;
import com.etg.salesforce.SalesforceSync;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Send + persistence boundary. Every outbound send is recorded with an idempotency key;
 * Twilio retries / double-submits return the original row without re-sending.
 * Every DLR is appended and rolls the parent message status forward.
 * Business writes and outbox events commit atomically for the RabbitMQ relay.
 */
@Service
public class MessageService {
  private final MessageRepository messages;
  private final DeliveryRepository deliveries;
  private final OutboxRepository outbox;
  private final TwilioSender sender;
  private final SendPolicy policy;
  private final ObjectMapper json;
  private final SalesforceSync sync;

  public MessageService(MessageRepository messages, DeliveryRepository deliveries,
                        OutboxRepository outbox, TwilioSender sender,
                        SendPolicy policy, ObjectMapper json, SalesforceSync sync) {
    this.messages = messages;
    this.deliveries = deliveries;
    this.outbox = outbox;
    this.sender = sender;
    this.policy = policy;
    this.json = json;
    this.sync = sync;
  }

  @Transactional
  public Message send(String toPhone, String topic, String body,
                      String idempotencyKey, String recipientTimezone) {
    return messages.findByIdempotencyKey(idempotencyKey)
        .orElseGet(() -> {
          policy.check(toPhone, topic, recipientTimezone);
          String sid = sender.send(toPhone, body);
          Message m = new Message("default", toPhone, null, "sms", topic,
              sha256Hex(body), "queued", sid, idempotencyKey);
          try {
            Message saved = messages.save(m);
            outbox.save(new OutboxEvent("message", String.valueOf(saved.getId()),
                "message.created", toJson(Map.of("to", toPhone, "topic", topic,
                    "sid", sid, "key", idempotencyKey))));
            sync.logSend(toPhone, topic, sid);
            return saved;
          } catch (DataIntegrityViolationException race) {
            // Lost a concurrent insert race on idempotency_key — return the winner.
            return messages.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> race);
          }
        });
  }

  @Transactional
  public void recordDelivery(String messageSid, String status, String errorCode) {
    if (messageSid == null || messageSid.isBlank() || status == null || status.isBlank()) return;
    Delivery saved = deliveries.save(new Delivery(messageSid, status.toLowerCase(), errorCode));
    messages.findByTwilioSid(messageSid).ifPresent(m -> {
      m.setStatus(status.toLowerCase());
      messages.save(m);
    });
    outbox.save(new OutboxEvent("delivery", String.valueOf(saved.getId()),
        "delivery.updated", toJson(Map.of("sid", messageSid, "status", status.toLowerCase(),
            "errorCode", errorCode == null ? "" : errorCode))));
  }

  public static String sha256Hex(String body) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private String toJson(Map<String, String> m) {
    try {
      return json.writeValueAsString(m);
    } catch (Exception e) {
      throw new IllegalStateException("Outbox JSON serialization failed", e);
    }
  }
}
