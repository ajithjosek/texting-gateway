package com.etg.messaging;

import com.etg.consent.ConsentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/messages")
public class MessageController {
  private final ConsentService consent;
  private final TwilioSender sender;

  public MessageController(ConsentService consent, TwilioSender sender) {
    this.consent = consent; this.sender = sender;
  }

  public record SendRequest(@NotBlank String toE164, @NotBlank String topic,
                            @NotBlank String body, String idempotencyKey) {}

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> send(@Valid @RequestBody SendRequest req,
      @Value("${etg.messaging-service-sid:}") String defaultService,
      @RequestHeader(value = "Idempotency-Key", required = false) String headerKey) {
    String key = (req.idempotencyKey() != null) ? req.idempotencyKey()
        : (headerKey != null ? headerKey : UUID.randomUUID().toString());
    consent.requireOptIn(req.toE164(), req.topic()); // TCPA gate (ADR-007)
    String sid = sender.send(req.toE164(), req.body());
    return Map.of("sid", sid, "idempotencyKey", key, "status", "queued");
  }
}
