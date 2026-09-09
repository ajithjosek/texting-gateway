package com.etg.messaging;

import com.twilio.security.RequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/** Validates X-Twilio-Signature, acks <500ms, then delegates to KeywordRouter. See docs/00-design-decisions.md sequences. */
@RestController
@RequestMapping("/twilio")
public class TwilioWebhookController {
  private final KeywordRouter router;
  private final MessageService messages;

  public TwilioWebhookController(KeywordRouter router, MessageService messages) {
    this.router = router; this.messages = messages;
  }

  @Value("${TWILIO_AUTH_TOKEN:}") private String authToken;

  private boolean valid(HttpServletRequest req) {
    if (authToken == null || authToken.isBlank()) return true; // local dev
    String sig = req.getHeader("X-Twilio-Signature");
    String url = req.getRequestURL().toString();
    Map<String, String> params = new HashMap<>();
    req.getParameterMap().forEach((k, v) -> params.put(k, v[0]));
    return new RequestValidator(authToken).validate(url, params, sig);
  }

  private static String twiml(String text) {
    String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    return "<Response><Message>" + safe + "</Message></Response>";
  }

  @PostMapping(value = "/inbound", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
      produces = MediaType.APPLICATION_XML_VALUE)
  public String inbound(HttpServletRequest req,
      @RequestParam(value = "From", required = false) String from,
      @RequestParam(value = "Body", required = false, defaultValue = "") String body) {
    if (!valid(req)) return "<Response></Response>";
    return twiml(router.route(from, body));
  }

  @PostMapping(value = "/dlr", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public void dlr(HttpServletRequest req,
      @RequestParam(value = "MessageSid", required = false) String sid,
      @RequestParam(value = "MessageStatus", required = false) String status,
      @RequestParam(value = "ErrorCode", required = false) String errorCode) {
    messages.recordDelivery(sid, status, errorCode);
  }
}
