package com.etg.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.etg.claims.PhotoIntakeService;
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

  @Mock KeywordRouter router;
  @Mock MessageService messages;
  @Mock PhotoIntakeService photos;
  TwilioWebhookController controller;

  @BeforeEach
  void setUp() {
    controller = new TwilioWebhookController(router, messages, photos);
    ReflectionTestUtils.setField(controller, "authToken", ""); // local-dev: skip signature check
  }

  private MockHttpServletRequest req() {
    return new MockHttpServletRequest();
  }

  @Test
  void inbound_delegatesToRouter_andWrapsInTwiml() {
    when(router.route("+15550009333", "BAL")).thenReturn("Balance $5 & change <soon>");
    String twiml = controller.inbound(req(), "+15550009333", "BAL");
    assertThat(twiml).startsWith("<Response><Message>").contains("Balance $5 &amp; change &lt;soon&gt;");
    verify(router).route("+15550009333", "BAL");
    verifyNoInteractions(photos);
  }

  @Test
  void inbound_withMedia_delegatesToPhotoIntake() {
    MockHttpServletRequest r = req();
    r.setParameter("NumMedia", "1");
    r.setParameter("MediaUrl0", "https://api.twilio.com/media/1");
    r.setParameter("MediaContentType0", "image/jpeg");
    when(photos.handle(eq("+15550009338"), eq("CLM-1001"), anyList()))
        .thenReturn("Photo received for CLM-1001.");
    String twiml = controller.inbound(r, "+15550009338", "CLM-1001");
    assertThat(twiml).contains("Photo received");
    verifyNoInteractions(router);
  }

  @Test
  void inbound_invalidSignature_returnsEmptyResponse_withoutRouting() {
    ReflectionTestUtils.setField(controller, "authToken", "secret");
    // No X-Twilio-Signature header -> validator rejects
    assertThat(controller.inbound(req(), "+15550009337", "STOP")).isEqualTo("<Response></Response>");
    verifyNoInteractions(router, photos);
  }

  @Test
  void dlr_delegatesToMessageService() {
    controller.dlr(req(), "SM1", "delivered", null);
    verify(messages).recordDelivery("SM1", "delivered", null);
  }
}
