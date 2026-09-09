package com.etg.api;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.etg.messaging.DeliveryRepository;
import com.etg.messaging.MessageRepository;
import com.etg.messaging.MessageService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/** Persistence contract: sends create message rows, DLRs append deliveries and roll status. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MessagePersistenceApiTest {

  @Autowired MockMvc mvc;
  @Autowired MessageRepository messages;
  @Autowired DeliveryRepository deliveries;
  @MockBean Clock clock;

  @BeforeEach
  void fixedDaytime() {
    // Deterministic send-governance: 10:00 America/New_York, marketing allowed.
    Instant now = Instant.parse("2026-06-01T14:00:00Z");
    lenient().when(clock.instant()).thenReturn(now);
    lenient().when(clock.withZone(any(ZoneId.class)))
        .thenAnswer(i -> Clock.fixed(now, i.getArgument(0)));
  }

  private void optIn(String phone, String topic) throws Exception {
    mvc.perform(post("/v1/consent/opt-in").contentType(MediaType.APPLICATION_JSON).content(
        "{\"phoneE164\":\"" + phone + "\",\"topic\":\"" + topic
            + "\",\"source\":\"api\",\"proofTextVersion\":\"v1\",\"actor\":\"test\"}"))
        .andExpect(status().isOk());
  }

  private void send(String phone, String topic, String body, String key) throws Exception {
    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON).content(
        "{\"toE164\":\"" + phone + "\",\"topic\":\"" + topic + "\",\"body\":\"" + body
            + "\",\"idempotencyKey\":\"" + key + "\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void send_persistsRowWithHash_notPlainBody() throws Exception {
    optIn("+15550005101", "servicing");
    send("+15550005101", "servicing", "renewal due", "persist-1");

    var row = messages.findByIdempotencyKey("persist-1").orElseThrow();
    assertThat(row.getToPhone()).isEqualTo("+15550005101");
    assertThat(row.getTopic()).isEqualTo("servicing");
    assertThat(row.getStatus()).isEqualTo("queued");
    assertThat(row.getTwilioSid()).isNotBlank();
    assertThat(row.getBodyHash()).isEqualTo(MessageService.sha256Hex("renewal due"));
  }

  @Test
  void resend_sameKey_doesNotDuplicateRow() throws Exception {
    optIn("+15550005102", "billing");
    send("+15550005102", "billing", "due", "persist-2");
    send("+15550005102", "billing", "due", "persist-2");

    assertThat(messages.findAll().stream()
        .filter(m -> "persist-2".equals(m.getIdempotencyKey())).count()).isEqualTo(1);
  }

  @Test
  void dlr_appendsDelivery_andUpdatesMessageStatus() throws Exception {
    optIn("+15550005103", "claims");
    send("+15550005103", "claims", "status update", "persist-3");
    String sid = messages.findByIdempotencyKey("persist-3").orElseThrow().getTwilioSid();

    mvc.perform(post("/twilio/dlr").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("MessageSid", sid).param("MessageStatus", "delivered"))
        .andExpect(status().isOk());

    assertThat(messages.findByIdempotencyKey("persist-3").orElseThrow().getStatus())
        .isEqualTo("delivered");
    assertThat(deliveries.findByMessageSidOrderByCreatedAtAsc(sid))
        .extracting("status").containsExactly("delivered");
  }

  @Test
  void dlr_failure_recordsErrorCode() throws Exception {
    optIn("+15550005104", "marketing");
    send("+15550005104", "marketing", "promo", "persist-4");
    String sid = messages.findByIdempotencyKey("persist-4").orElseThrow().getTwilioSid();

    mvc.perform(post("/twilio/dlr").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("MessageSid", sid).param("MessageStatus", "failed").param("ErrorCode", "30007"))
        .andExpect(status().isOk());

    assertThat(messages.findByIdempotencyKey("persist-4").orElseThrow().getStatus())
        .isEqualTo("failed");
    assertThat(deliveries.findByMessageSidOrderByCreatedAtAsc(sid).get(0).getErrorCode())
        .isEqualTo("30007");
  }
}
