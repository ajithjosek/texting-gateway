package com.etg.hub.billing;

import com.etg.consent.ConsentService;
import com.etg.links.LinkSigner;
import com.etg.messaging.MessageService;
import com.etg.messaging.PhoneValidator;
import com.etg.template.TemplateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * BillingCenter event ingress (S2). Each event renders its template and sends once —
 * the BillingCenter event id becomes the message idempotency key, so retried
 * webhooks never double-text. Real Guidewire Cloud API polling lands in S3; the
 * contract here is already the adapter seam.
 */
@RestController
@RequestMapping("/v1/hub/billing")
public class BillingWebhookController {
  private final PhoneValidator phones;
  private final ConsentService consent;
  private final TemplateService templates;
  private final MessageService messages;
  private final LinkSigner links;
  private final String publicBaseUrl;

  public BillingWebhookController(PhoneValidator phones, ConsentService consent,
                                  TemplateService templates, MessageService messages,
                                  LinkSigner links,
                                  @Value("${etg.public-base-url:http://localhost:8080}") String publicBaseUrl) {
    this.phones = phones;
    this.consent = consent;
    this.templates = templates;
    this.messages = messages;
    this.links = links;
    this.publicBaseUrl = publicBaseUrl;
  }

  public record BillingEvent(@NotBlank String eventId, @NotBlank String type,
                             @NotBlank String phone, String firstName,
                             String policyNumber, long amountCents, String dueDate) {}

  @PostMapping("/events")
  public Map<String, String> ingest(@Valid @RequestBody BillingEvent e) {
    phones.validate(e.phone());
    consent.requireOptIn(e.phone(), "billing");
    String templateKey = switch (e.type().toUpperCase()) {
      case "FAILED" -> "payment_failed";
      case "RECEIPT" -> "payment_receipt";
      default -> "payment_due";
    };
    Map<String, Object> vars = new HashMap<>();
    vars.put("first_name", e.firstName() == null ? "there" : e.firstName());
    vars.put("policy_number", e.policyNumber() == null ? "" : e.policyNumber());
    vars.put("amount", String.format("$%,.2f", e.amountCents() / 100.0));
    vars.put("due_date", e.dueDate() == null ? "" : e.dueDate());
    if (!"RECEIPT".equalsIgnoreCase(e.type())) {
      vars.put("pay_link", publicBaseUrl + "/pay?t="
          + links.sign("pay", e.policyNumber(), Duration.ofDays(7)));
    } else {
      vars.put("receipt_link", publicBaseUrl + "/receipt?t="
          + links.sign("receipt", e.eventId(), Duration.ofDays(30)));
    }
    String body = templates.render(templateKey, "en", vars);
    var saved = messages.send(e.phone(), "billing", body, "billing:" + e.eventId(), null);
    return Map.of("sid", saved.getTwilioSid(), "status", saved.getStatus());
  }
}
