package com.etg.messaging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Send + persistence boundary. Every outbound send is recorded with an idempotency key;
 * Twilio retries / double-submits return the original row without re-sending.
 * Every DLR is appended and rolls the parent message status forward.
 */
@Service
public class MessageService {
  private final MessageRepository messages;
  private final DeliveryRepository deliveries;
  private final TwilioSender sender;

  public MessageService(MessageRepository messages, DeliveryRepository deliveries, TwilioSender sender) {
    this.messages = messages;
    this.deliveries = deliveries;
    this.sender = sender;
  }

  @Transactional
  public Message send(String toPhone, String topic, String body, String idempotencyKey) {
    return messages.findByIdempotencyKey(idempotencyKey)
        .orElseGet(() -> {
          String sid = sender.send(toPhone, body);
          Message m = new Message("default", toPhone, null, "sms", topic,
              sha256Hex(body), "queued", sid, idempotencyKey);
          try {
            return messages.save(m);
          } catch (DataIntegrityViolationException race) {
            // Lost a concurrent insert race on idempotency_key — return the winner.
            return messages.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> race);
          }
        });
  }

  @Transactional
  public void recordDelivery(String messageSid, String status, String errorCode) {
    if (messageSid == null || messageSid.isBlank() || status == null || status.isBlank()) return;
    deliveries.save(new Delivery(messageSid, status.toLowerCase(), errorCode));
    messages.findByTwilioSid(messageSid).ifPresent(m -> {
      m.setStatus(status.toLowerCase());
      messages.save(m);
    });
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
}
