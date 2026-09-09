package com.etg.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.etg.outbox.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Outbox contract: sends and DLRs enqueue relayable events atomically. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OutboxApiTest {

  @Autowired MockMvc mvc;
  @Autowired OutboxRepository outbox;
  @Autowired com.etg.messaging.MessageRepository messages;

  private void optIn(String phone, String topic) throws Exception {
    mvc.perform(post("/v1/consent/opt-in").contentType(MediaType.APPLICATION_JSON).content(
        "{\"phoneE164\":\"" + phone + "\",\"topic\":\"" + topic
            + "\",\"source\":\"api\",\"proofTextVersion\":\"v1\",\"actor\":\"test\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void send_enqueuesMessageCreated() throws Exception {
    optIn("+15550008101", "servicing");
    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON).content("""
            {"toE164":"+15550008101","topic":"servicing","body":"hi","idempotencyKey":"ob-1"}"""))
        .andExpect(status().isOk());

    var pending = outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
    assertThat(pending).extracting("eventType").contains("message.created");
    assertThat(pending.stream()
        .filter(e -> "message.created".equals(e.getEventType())).findFirst().orElseThrow()
        .getPayload()).contains("+15550008101");
  }

  @Test
  void dlr_enqueuesDeliveryUpdated() throws Exception {
    optIn("+15550008102", "billing");
    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON).content("""
            {"toE164":"+15550008102","topic":"billing","body":"due","idempotencyKey":"ob-2"}"""))
        .andExpect(status().isOk());
    String sid = messages.findByIdempotencyKey("ob-2").orElseThrow().getTwilioSid();

    mvc.perform(post("/twilio/dlr").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("MessageSid", sid).param("MessageStatus", "delivered"))
        .andExpect(status().isOk());

    assertThat(outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
        .extracting("eventType").contains("message.created", "delivery.updated");
  }
}
