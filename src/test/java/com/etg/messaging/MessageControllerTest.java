package com.etg.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.etg.consent.ConsentDeniedException;
import com.etg.consent.ConsentService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

  @Mock ConsentService consent;
  @Mock TwilioSender sender;
  @InjectMocks MessageController controller;

  @Test
  void send_blockedWhenNoConsent() {
    doThrow(new ConsentDeniedException("CONSENT_DENIED_no_opt_in"))
        .when(consent).requireOptIn("+15550009222", "marketing");
    var req = new MessageController.SendRequest("+15550009222", "marketing", "hi", null);
    assertThatThrownBy(() -> controller.send(req, "", null))
        .isInstanceOf(ConsentDeniedException.class);
    verifyNoInteractions(sender);
  }

  @Test
  void send_usesBodyIdempotencyKey_andReturnsSenderSid() {
    when(sender.send("+15550009223", "renewal due")).thenReturn("SM123");
    var req = new MessageController.SendRequest("+15550009223", "servicing", "renewal due", "key-1");
    Map<String, String> res = controller.send(req, "", null);
    assertThat(res).containsEntry("sid", "SM123").containsEntry("idempotencyKey", "key-1");
    verify(consent).requireOptIn("+15550009223", "servicing");
  }

  @Test
  void send_fallsBackToHeaderKey_thenGeneratesUuid() {
    when(sender.send(anyString(), anyString())).thenReturn("SM999");
    var fromHeader = new MessageController.SendRequest("+15550009224", "billing", "due", null);
    assertThat(controller.send(fromHeader, "", "hdr-9")).containsEntry("idempotencyKey", "hdr-9");
    var generated = new MessageController.SendRequest("+15550009225", "billing", "due", null);
    assertThat(controller.send(generated, "", null).get("idempotencyKey")).isNotBlank();
  }
}
