package com.etg.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etg.consent.ConsentDeniedException;
import com.etg.consent.ConsentService;
import com.etg.outbox.OutboxRepository;
import com.etg.salesforce.SalesforceSync;
import com.etg.whatsapp.ContentTemplate;
import com.etg.whatsapp.WhatsappTemplateService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

  @Mock MessageRepository messages;
  @Mock DeliveryRepository deliveries;
  @Mock OutboxRepository outbox;
  @Mock TwilioSender sender;
  @Mock SendPolicy policy;
  @Mock SalesforceSync sync;
  @Mock ConsentService consentService;
  @Mock WhatsappTemplateService whatsapp;
  @Spy com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
  @InjectMocks MessageService service;

  @Test
  void send_persistsRowWithBodyHash_andReturnsIt() {
    when(messages.findByIdempotencyKey("k-1")).thenReturn(Optional.empty());
    when(sender.send("+15550005001", "renewal due")).thenReturn("SM111");
    when(messages.save(any(Message.class))).thenAnswer(i -> i.getArgument(0));

    Message m = service.send("+15550005001", "servicing", "renewal due", "k-1", null);

    assertThat(m.getTwilioSid()).isEqualTo("SM111");
    assertThat(m.getStatus()).isEqualTo("queued");
    assertThat(m.getBodyHash()).hasSize(64).doesNotContain("renewal due");
    assertThat(m.getBodyHash()).isEqualTo(MessageService.sha256Hex("renewal due"));
    verify(policy).check("+15550005001", "servicing", null);
    verify(outbox).save(argThat(e ->
        "message.created".equals(e.getEventType()) && e.getPayload().contains("SM111")));
  }

  @Test
  void send_policyDenial_blocksWithoutSendingOrSaving() {
    when(messages.findByIdempotencyKey("k-9")).thenReturn(Optional.empty());
    doThrow(new ConsentDeniedException("CONSENT_DENIED_quiet_hours"))
        .when(policy).check("+15550005009", "marketing", "America/New_York");

    assertThatThrownBy(
        () -> service.send("+15550005009", "marketing", "promo", "k-9", "America/New_York"))
        .isInstanceOf(ConsentDeniedException.class);
    verifyNoInteractions(sender);
    verify(messages, never()).save(any());
  }

  @Test
  void send_duplicateKey_returnsOriginal_withoutResending() {
    Message original = new Message("default", "+15550005002", null, "sms", "billing",
        "hash", "queued", "SM222", "k-2");
    when(messages.findByIdempotencyKey("k-2")).thenReturn(Optional.of(original));

    assertThat(service.send("+15550005002", "billing", "due", "k-2", null)).isSameAs(original);
    verifyNoInteractions(sender);
    verify(messages, never()).save(any());
  }

  @Test
  void send_lostInsertRace_returnsWinner() {
    Message winner = new Message("default", "+15550005003", null, "sms", "billing",
        "hash", "queued", "SM333", "k-3");
    when(messages.findByIdempotencyKey("k-3"))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(winner));
    when(sender.send(anyString(), anyString())).thenReturn("SM-racy");
    when(messages.save(any())).thenThrow(new DataIntegrityViolationException("dup key"));

    assertThat(service.send("+15550005003", "billing", "due", "k-3", null)).isSameAs(winner);
  }

  @Test
  void recordDelivery_appendsRow_rollsStatus_andEmitsEvent() {
    Message m = new Message("default", "+15550005004", null, "sms", "servicing",
        "hash", "queued", "SM444", "k-4");
    when(messages.findByTwilioSid("SM444")).thenReturn(Optional.of(m));
    when(deliveries.save(any(Delivery.class))).thenAnswer(i -> i.getArgument(0));

    service.recordDelivery("SM444", "Delivered", null);

    assertThat(m.getStatus()).isEqualTo("delivered");
    verify(deliveries).save(argThat(d ->
        "SM444".equals(d.getMessageSid()) && "delivered".equals(d.getStatus())));
    verify(messages).save(m);
    verify(outbox).save(argThat(e ->
        "delivery.updated".equals(e.getEventType()) && e.getPayload().contains("SM444")));
  }

  @Test
  void recordDelivery_unknownSid_stillAppendsRow() {
    when(messages.findByTwilioSid("SM-unknown")).thenReturn(Optional.empty());
    when(deliveries.save(any(Delivery.class))).thenAnswer(i -> i.getArgument(0));

    service.recordDelivery("SM-unknown", "failed", "30007");

    verify(deliveries).save(argThat(d ->
        "failed".equals(d.getStatus()) && "30007".equals(d.getErrorCode())));
  }

  @Test
  void recordDelivery_blankInput_isNoop() {
    service.recordDelivery(null, "delivered", null);
    service.recordDelivery("SM555", " ", null);
    verifyNoInteractions(deliveries, messages);
  }

  private ContentTemplate approved(String sid) {
    ContentTemplate t = new ContentTemplate("promo", "whatsapp", sid);
    t.setStatus(ContentTemplate.APPROVED);
    return t;
  }

  @Test
  void rich_whatsappApproved_sendsWhatsapp() {
    when(messages.findByIdempotencyKey("k-wa")).thenReturn(Optional.empty());
    when(whatsapp.approvedFor("HX1")).thenReturn(Optional.of(approved("HX1")));
    when(sender.sendWhatsapp("+15550005010", "HX1", "{\"1\":\"Ana\"}")).thenReturn("SMwa");
    when(messages.save(any(Message.class))).thenAnswer(i -> i.getArgument(0));

    Message m = service.sendRich("+15550005010", "servicing", "hi", "k-wa", null,
        "whatsapp", "HX1", "{\"1\":\"Ana\"}");

    assertThat(m.getTwilioSid()).isEqualTo("SMwa");
    assertThat(m.getChannel()).isEqualTo("whatsapp");
    verify(sender, never()).send(anyString(), anyString());
  }

  @Test
  void rich_whatsappUnapproved_throwsWithoutSending() {
    when(messages.findByIdempotencyKey("k-wa2")).thenReturn(Optional.empty());
    when(whatsapp.approvedFor("HX9")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.sendRich("+15550005011", "servicing", "hi", "k-wa2",
        null, "whatsapp", "HX9", null))
        .isInstanceOf(ChannelNotAvailableException.class);
    verifyNoInteractions(sender);
    verify(messages, never()).save(any());
  }

  @Test
  void rich_auto_fallsBackToSms_withoutApproval() {
    when(messages.findByIdempotencyKey("k-auto")).thenReturn(Optional.empty());
    when(whatsapp.approvedFor(null)).thenReturn(Optional.empty());
    when(sender.send("+15550005012", "hi")).thenReturn("SMs");
    when(messages.save(any(Message.class))).thenAnswer(i -> i.getArgument(0));

    Message m = service.sendRich("+15550005012", "servicing", "hi", "k-auto", null,
        "auto", null, null);

    assertThat(m.getChannel()).isEqualTo("sms");
    assertThat(m.getTwilioSid()).isEqualTo("SMs");
  }

  @Test
  void rich_whatsappSenderFailure_fallsBackToSms() {
    when(messages.findByIdempotencyKey("k-waf")).thenReturn(Optional.empty());
    when(whatsapp.approvedFor("HX1")).thenReturn(Optional.of(approved("HX1")));
    when(sender.sendWhatsapp(anyString(), anyString(), any())).thenThrow(new RuntimeException("wa down"));
    when(sender.send("+15550005013", "hi")).thenReturn("SMfb");
    when(messages.save(any(Message.class))).thenAnswer(i -> i.getArgument(0));

    Message m = service.sendRich("+15550005013", "servicing", "hi", "k-waf", null,
        "whatsapp", "HX1", null);

    assertThat(m.getChannel()).isEqualTo("sms");
    assertThat(m.getTwilioSid()).isEqualTo("SMfb");
  }
}
