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
  @Mock MessageService messages;
  @Mock PhoneValidator phones;
  @InjectMocks MessageController controller;

  private static Message saved(String sid, String key, String status) {
    return new Message("default", "+15550009223", null, "sms", "servicing",
        "hash", status, sid, key);
  }

  private static MessageController.SendRequest req(String to, String topic, String body,
                                                   String key, String tz) {
    return new MessageController.SendRequest(to, topic, body, key, tz, null, null, null);
  }

  @Test
  void send_blockedWhenNoConsent() {
    doThrow(new ConsentDeniedException("CONSENT_DENIED_no_opt_in"))
        .when(consent).requireOptIn("+15550009222", "marketing");
    assertThatThrownBy(() -> controller.send(req("+15550009222", "marketing", "hi", null, null), null))
        .isInstanceOf(ConsentDeniedException.class);
    verifyNoInteractions(messages);
  }

  @Test
  void send_usesBodyIdempotencyKey_andReturnsStoredRow() {
    when(messages.sendRich("+15550009223", "servicing", "renewal due", "key-1",
        "America/New_York", null, null, null)).thenReturn(saved("SM123", "key-1", "queued"));
    Map<String, String> res = controller.send(
        req("+15550009223", "servicing", "renewal due", "key-1", "America/New_York"), null);
    assertThat(res).containsEntry("sid", "SM123").containsEntry("idempotencyKey", "key-1");
    verify(consent).requireOptIn("+15550009223", "servicing");
  }

  @Test
  void send_fallsBackToHeaderKey_thenGeneratesUuid() {
    when(messages.sendRich(eq("+15550009224"), eq("billing"), eq("due"), eq("hdr-9"),
        isNull(), isNull(), isNull(), isNull())).thenReturn(saved("SM1", "hdr-9", "queued"));
    assertThat(controller.send(req("+15550009224", "billing", "due", null, null), "hdr-9"))
        .containsEntry("idempotencyKey", "hdr-9");

    when(messages.sendRich(eq("+15550009225"), eq("billing"), eq("due"),
        argThat(k -> k != null && !k.isBlank()), isNull(), isNull(), isNull(), isNull()))
        .thenAnswer(i -> saved("SM2", i.getArgument(3), "queued"));
    assertThat(controller.send(req("+15550009225", "billing", "due", null, null), null)
        .get("idempotencyKey")).isNotBlank();
  }

  @Test
  void send_passesChannelFields_through() {
    when(messages.sendRich("+15550009226", "servicing", "hi", "k-8", null,
        "whatsapp", "HX1", "{\"1\":\"Ana\"}")).thenReturn(saved("SMw", "k-8", "queued"));
    Map<String, String> res = controller.send(new MessageController.SendRequest(
        "+15550009226", "servicing", "hi", "k-8", null, "whatsapp", "HX1", "{\"1\":\"Ana\"}"), null);
    assertThat(res).containsEntry("sid", "SMw");
  }
}
