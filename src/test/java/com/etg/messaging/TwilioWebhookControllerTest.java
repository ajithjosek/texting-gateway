package com.etg.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.etg.consent.ConsentService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TwilioWebhookControllerTest {

  @Mock ConsentService consent;
  @Mock MessageService messages;
  TwilioWebhookController controller;

  @BeforeEach
  void setUp() {
    controller = new TwilioWebhookController(consent, messages);
    ReflectionTestUtils.setField(controller, "authToken", ""); // local-dev: skip signature check
  }

  private HttpServletRequest req() {
    return new MockHttpServletRequest();
  }

  @Test
  void inbound_stop_optsOutBothTopics_andConfirms() {
    String twiml = controller.inbound(req(), "+15550009333", "STOP");
    assertThat(twiml).contains("unsubscribed");
    verify(consent).optOut("+15550009333", "marketing", "keyword", "twilio");
    verify(consent).optOut("+15550009333", "servicing", "keyword", "twilio");
  }

  @Test
  void inbound_stopVariants_caseInsensitive_withTrailingWords() {
    assertThat(controller.inbound(req(), "+15550009334", "stop all please")).contains("unsubscribed");
    assertThat(controller.inbound(req(), "+15550009334", "Unsubscribe")).contains("unsubscribed");
    assertThat(controller.inbound(req(), "+15550009334", "QUIT")).contains("unsubscribed");
  }

  @Test
  void inbound_help_returnsHelpText_withoutTouchingConsent() {
    assertThat(controller.inbound(req(), "+15550009335", "help")).contains("Reply STOP");
    verifyNoInteractions(consent);
  }

  @Test
  void inbound_unknownBody_routesToAgentInbox() {
    assertThat(controller.inbound(req(), "+15550009336", "what is my balance?")).contains("agent will reply");
    verifyNoInteractions(consent);
  }

  @Test
  void inbound_invalidSignature_returnsEmptyResponse() {
    ReflectionTestUtils.setField(controller, "authToken", "secret");
    // No X-Twilio-Signature header -> validator rejects
    assertThat(controller.inbound(req(), "+15550009337", "STOP")).isEqualTo("<Response></Response>");
    verifyNoInteractions(consent);
  }
}
