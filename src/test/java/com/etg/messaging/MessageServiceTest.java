package com.etg.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

  @Mock MessageRepository messages;
  @Mock DeliveryRepository deliveries;
  @Mock TwilioSender sender;
  @InjectMocks MessageService service;

  @Test
  void send_persistsRowWithBodyHash_andReturnsIt() {
    when(messages.findByIdempotencyKey("k-1")).thenReturn(Optional.empty());
    when(sender.send("+15550005001", "renewal due")).thenReturn("SM111");
    when(messages.save(any(Message.class))).thenAnswer(i -> i.getArgument(0));

    Message m = service.send("+15550005001", "servicing", "renewal due", "k-1");

    assertThat(m.getTwilioSid()).isEqualTo("SM111");
    assertThat(m.getStatus()).isEqualTo("queued");
    assertThat(m.getBodyHash()).hasSize(64).doesNotContain("renewal due");
    assertThat(m.getBodyHash()).isEqualTo(MessageService.sha256Hex("renewal due"));
  }

  @Test
  void send_duplicateKey_returnsOriginal_withoutResending() {
    Message original = new Message("default", "+15550005002", null, "sms", "billing",
        "hash", "queued", "SM222", "k-2");
    when(messages.findByIdempotencyKey("k-2")).thenReturn(Optional.of(original));

    assertThat(service.send("+15550005002", "billing", "due", "k-2")).isSameAs(original);
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

    assertThat(service.send("+15550005003", "billing", "due", "k-3")).isSameAs(winner);
  }

  @Test
  void recordDelivery_appendsRow_andRollsMessageStatus() {
    Message m = new Message("default", "+15550005004", null, "sms", "servicing",
        "hash", "queued", "SM444", "k-4");
    when(messages.findByTwilioSid("SM444")).thenReturn(Optional.of(m));

    service.recordDelivery("SM444", "Delivered", null);

    assertThat(m.getStatus()).isEqualTo("delivered");
    verify(deliveries).save(argThat(d ->
        "SM444".equals(d.getMessageSid()) && "delivered".equals(d.getStatus())));
    verify(messages).save(m);
  }

  @Test
  void recordDelivery_unknownSid_stillAppendsRow() {
    when(messages.findByTwilioSid("SM-unknown")).thenReturn(Optional.empty());

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
}
