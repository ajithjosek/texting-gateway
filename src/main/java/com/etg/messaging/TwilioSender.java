package com.etg.messaging;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Thin adapter over Twilio Java SDK. Business rules (consent, quiet hours) live outside this class. */
@Service
public class TwilioSender {
  @Value("${TWILIO_ACCOUNT_SID:}") private String accountSid;
  @Value("${TWILIO_AUTH_TOKEN:}") private String authToken;
  @Value("${TWILIO_MESSAGING_SERVICE_SID:}") private String messagingServiceSid;

  @PostConstruct
  void init() {
    if (!accountSid.isBlank() && !authToken.isBlank()) {
      Twilio.init(accountSid, authToken);
    }
  }

  public String send(String toE164, String body) {
    if (accountSid.isBlank() || messagingServiceSid.isBlank()) {
      return "SM-stub-no-credentials"; // local dev without Twilio creds
    }
    Message m = Message.creator(new PhoneNumber(toE164), messagingServiceSid, body).create();
    return m.getSid();
  }
}
