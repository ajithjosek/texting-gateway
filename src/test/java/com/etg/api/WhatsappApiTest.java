package com.etg.api;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.etg.messaging.MessageRepository;
import com.etg.whatsapp.ContentTemplate;
import com.etg.whatsapp.ContentTemplateRepository;
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

/** WhatsApp channel contract: approval gate, auto fallback, channel recorded. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WhatsappApiTest {

  @Autowired MockMvc mvc;
  @Autowired MessageRepository messages;
  @Autowired ContentTemplateRepository content;
  @MockBean Clock clock;

  @BeforeEach
  void daytime() {
    Instant now = Instant.parse("2026-06-01T14:00:00Z"); // 10:00 ET
    lenient().when(clock.instant()).thenReturn(now);
    lenient().when(clock.withZone(any(ZoneId.class)))
        .thenAnswer(i -> Clock.fixed(now, i.getArgument(0)));
  }

  private void optIn(String phone, String topic) throws Exception {
    mvc.perform(post("/v1/consent/opt-in").contentType(MediaType.APPLICATION_JSON).content(
        "{\"phoneE164\":\"" + phone + "\",\"topic\":\"" + topic
            + "\",\"source\":\"api\",\"proofTextVersion\":\"v1\"}"))
        .andExpect(status().isOk());
  }

  private void approve(String sid) {
    ContentTemplate t = content.findFirstByContentSidAndActiveTrue(sid).orElseThrow();
    t.setStatus(ContentTemplate.APPROVED);
    content.save(t);
  }

  @Test
  void whatsapp_requiresApproval() throws Exception {
    optIn("+15550017001", "servicing");
    mvc.perform(post("/v1/whatsapp/templates").contentType(MediaType.APPLICATION_JSON).content(
        "{\"key\":\"promo\",\"channel\":\"whatsapp\",\"contentSid\":\"HX-pend\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("pending"));

    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON).content(
        "{\"toE164\":\"+15550017001\",\"topic\":\"servicing\",\"body\":\"hi\","
            + "\"idempotencyKey\":\"wa-1\",\"channel\":\"whatsapp\",\"contentSid\":\"HX-pend\"}"))
        .andExpect(status().isConflict());
    assertThat(messages.findByIdempotencyKey("wa-1")).isEmpty();
  }

  @Test
  void whatsapp_approved_sendsWhatsAppChannel() throws Exception {
    optIn("+15550017002", "servicing");
    mvc.perform(post("/v1/whatsapp/templates").contentType(MediaType.APPLICATION_JSON).content(
        "{\"key\":\"promo\",\"channel\":\"whatsapp\",\"contentSid\":\"HX-ok\"}"))
        .andExpect(status().isOk());
    approve("HX-ok");

    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON).content(
        "{\"toE164\":\"+15550017002\",\"topic\":\"servicing\",\"body\":\"hi\","
            + "\"idempotencyKey\":\"wa-2\",\"channel\":\"whatsapp\",\"contentSid\":\"HX-ok\","
            + "\"contentVariables\":\"{\\\"1\\\":\\\"Ana\\\"}\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.channel").value("whatsapp"));

    assertThat(messages.findByIdempotencyKey("wa-2").orElseThrow().getChannel())
        .isEqualTo("whatsapp");
  }

  @Test
  void auto_withoutApproval_sendsSms() throws Exception {
    optIn("+15550017003", "servicing");
    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON).content(
        "{\"toE164\":\"+15550017003\",\"topic\":\"servicing\",\"body\":\"hi\","
            + "\"idempotencyKey\":\"wa-3\",\"channel\":\"auto\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.channel").value("sms"));
  }

  @Test
  void sync_reportsUpdatedCount() throws Exception {
    mvc.perform(post("/v1/whatsapp/sync")).andExpect(status().isOk())
        .andExpect(jsonPath("$.updated", is(0))); // no creds: fail-open, nothing changes
  }
}
