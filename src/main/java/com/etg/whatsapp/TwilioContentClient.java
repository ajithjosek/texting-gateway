package com.etg.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

/**
 * Twilio Content API v2 client (template approval state). Fail-open: without
 * credentials or on any error it returns empty and local rows keep their state.
 */
@Component
public class TwilioContentClient {
  private static final Logger log = LoggerFactory.getLogger(TwilioContentClient.class);

  private final RestTemplate http;
  private final ObjectMapper json = new ObjectMapper();
  private final String baseUrl;
  private final String accountSid;
  private final String authToken;

  public TwilioContentClient(RestTemplateBuilder builder,
                             @Value("${etg.twilio.content-base:https://content.twilio.com}") String baseUrl,
                             @Value("${TWILIO_ACCOUNT_SID:}") String accountSid,
                             @Value("${TWILIO_AUTH_TOKEN:}") String authToken) {
    this.http = builder.build();
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.accountSid = accountSid;
    this.authToken = authToken;
    if (accountSid != null && !accountSid.isBlank() && authToken != null && !authToken.isBlank()) {
      this.http.getInterceptors().add(new BasicAuthenticationInterceptor(accountSid, authToken));
    }
  }

  /** WhatsApp approval status for a Content SID, lowercased (approved/pending/rejected). */
  public Optional<String> whatsappApproval(String contentSid) {
    if (!configured()) return Optional.empty();
    try {
      String payload = http.exchange(baseUrl + "/v2/Contents/" + contentSid + "/ApprovalRequests/whatsapp",
          HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class).getBody();
      JsonNode status = json.readTree(payload == null ? "{}" : payload).path("status");
      if (status.isMissingNode() || status.asText().isBlank()) return Optional.empty();
      return Optional.of(status.asText().toLowerCase());
    } catch (Exception e) {
      log.warn("Content API lookup failed for {}: {}", contentSid, e.getMessage());
      return Optional.empty();
    }
  }

  private boolean configured() {
    return accountSid != null && !accountSid.isBlank() && authToken != null && !authToken.isBlank();
  }

  RestTemplate http() {
    return http;
  }
}
