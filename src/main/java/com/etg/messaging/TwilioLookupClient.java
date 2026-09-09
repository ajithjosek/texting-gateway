package com.etg.messaging;

import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper over Twilio Lookup v2 (line_type_intelligence).
 * Fail-open: without credentials or on Lookup outage it returns empty and local
 * libphonenumber validation alone governs. Never blocks sends on Lookup downtime.
 */
@Component
public class TwilioLookupClient {
  private static final Logger log = LoggerFactory.getLogger(TwilioLookupClient.class);

  @Value("${TWILIO_ACCOUNT_SID:}") private String accountSid;
  @Value("${TWILIO_AUTH_TOKEN:}") private String authToken;

  public Optional<String> lineType(String e164) {
    if (accountSid == null || accountSid.isBlank() || authToken == null || authToken.isBlank()) {
      return Optional.empty();
    }
    try {
      Map<String, Object> lti = com.twilio.rest.lookups.v2.PhoneNumber
          .fetcher(e164)
          .setFields("line_type_intelligence")
          .fetch()
          .getLineTypeIntelligence();
      if (lti == null) return Optional.empty();
      Object type = lti.get("type");
      return type == null ? Optional.empty() : Optional.of(type.toString().toLowerCase());
    } catch (Exception e) {
      log.warn("Twilio Lookup failed for {}: {}", e164, e.getMessage());
      return Optional.empty();
    }
  }
}
