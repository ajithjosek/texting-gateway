package com.etg.messaging;

import com.etg.consent.ConsentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/messages")
public class MessageController {
  private final ConsentService consent;
  private final MessageService messages;
  private final PhoneValidator phones;

  public MessageController(ConsentService consent, MessageService messages, PhoneValidator phones) {
    this.consent = consent; this.messages = messages; this.phones = phones;
  }

  public record SendRequest(@NotBlank String toE164, @NotBlank String topic,
                            @NotBlank String body, String idempotencyKey,
                            String recipientTimezone) {}

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> send(@Valid @RequestBody SendRequest req,
      @RequestHeader(value = "Idempotency-Key", required = false) String headerKey) {
    String key = (req.idempotencyKey() != null) ? req.idempotencyKey()
        : (headerKey != null ? headerKey : UUID.randomUUID().toString());
    phones.validate(req.toE164());
    consent.requireOptIn(req.toE164(), req.topic()); // TCPA gate (ADR-007)
    Message saved = messages.send(req.toE164(), req.topic(), req.body(), key,
        req.recipientTimezone());
    return Map.of("sid", saved.getTwilioSid(), "idempotencyKey", saved.getIdempotencyKey(),
        "status", saved.getStatus());
  }
}
