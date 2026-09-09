package com.etg.messaging;

import com.etg.consent.ConsentService;
import com.twilio.security.RequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/** Validates X-Twilio-Signature, acks <500ms, then routes keywords. See docs/00-design-decisions.md sequences. */
@RestController
@RequestMapping("/twilio")
public class TwilioWebhookController {
  private final ConsentService consent;

  public TwilioWebhookController(ConsentService consent) { this.consent = consent; }

  @Value("${TWILIO_AUTH_TOKEN:}") private String authToken;

  private boolean valid(HttpServletRequest req) {
    if (authToken == null || authToken.isBlank()) return true; // local dev
    String sig = req.getHeader("X-Twilio-Signature");
    String url = req.getRequestURL().toString();
    Map<String, String> params = new HashMap<>();
    req.getParameterMap().forEach((k, v) -> params.put(k, v[0]));
    return new RequestValidator(authToken).validate(url, params, sig);
  }

  @PostMapping(value = "/inbound", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
      produces = MediaType.APPLICATION_XML_VALUE)
  public String inbound(HttpServletRequest req,
      @RequestParam(value = "From", required = false) String from,
      @RequestParam(value = "Body", required = false, defaultValue = "") String body) {
    if (!valid(req)) return "<Response></Response>";
    String keyword = body.trim().toUpperCase();
    if (keyword.startsWith("STOP") || keyword.startsWith("UNSUBSCRIBE") || keyword.startsWith("QUIT")) {
      consent.optOut(from, "marketing", "keyword", "twilio");
      consent.optOut(from, "servicing", "keyword", "twilio");
      return "<Response><Message>You have been unsubscribed. Reply HELP for help.</Message></Response>";
    }
    if (keyword.startsWith("HELP")) {
      return "<Response><Message>ETG alerts. Reply STOP to opt out. Help: support@example.com</Message></Response>";
    }
    // MVP: hand anything else to agent inbox (Phase-1 queue); keyword flows (BAL/STATUS/CLAIM) land here next.
    return "<Response><Message>Thanks — an agent will reply shortly.</Message></Response>";
  }

  @PostMapping(value = "/dlr", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public void dlr(HttpServletRequest req,
      @RequestParam(value = "MessageSid", required = false) String sid,
      @RequestParam(value = "MessageStatus", required = false) String status) {
    // Phase-1: persist to deliveries table + outbox -> CRM/warehouse. Stubbed for scaffold.
  }
}
