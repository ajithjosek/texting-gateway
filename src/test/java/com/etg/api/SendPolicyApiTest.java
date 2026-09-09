package com.etg.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.etg.consent.ConsentEventRepository;
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

/** Send-governance contract with a fixed clock: quiet hours, caps, and audit trail. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SendPolicyApiTest {

  @Autowired MockMvc mvc;
  @Autowired ConsentEventRepository events;
  @MockBean Clock clock;

  @BeforeEach
  void daytime() {
    stubClock("2026-06-01T14:00:00Z"); // 10:00 America/New_York
  }

  private void stubClock(String utcInstant) {
    Instant now = Instant.parse(utcInstant);
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

  private void send(String phone, String topic, String body, String key, String tz, int expected) throws Exception {
    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON).content(
        "{\"toE164\":\"" + phone + "\",\"topic\":\"" + topic + "\",\"body\":\"" + body
            + "\",\"idempotencyKey\":\"" + key + "\",\"recipientTimezone\":\"" + tz + "\"}"))
        .andExpect(status().is(expected));
  }

  @Test
  void marketing_blockedAtNight_allowedDuringDay() throws Exception {
    optIn("+15550006101", "marketing");
    stubClock("2026-06-01T02:00:00Z"); // 22:00 ET
    send("+15550006101", "marketing", "promo", "q-1", "America/New_York", 403);
    stubClock("2026-06-01T14:00:00Z"); // 10:00 ET
    send("+15550006101", "marketing", "promo", "q-2", "America/New_York", 200);
  }

  @Test
  void servicing_allowedAtNight() throws Exception {
    optIn("+15550006102", "servicing");
    stubClock("2026-06-01T03:00:00Z"); // 23:00 ET
    send("+15550006102", "servicing", "renewal", "q-3", "America/New_York", 200);
  }

  @Test
  void marketing_fifthSendIn30Days_blocked() throws Exception {
    optIn("+15550006103", "marketing");
    for (int i = 1; i <= 4; i++) {
      send("+15550006103", "marketing", "promo " + i, "cap-" + i, "America/New_York", 200);
    }
    send("+15550006103", "marketing", "promo 5", "cap-5", "America/New_York", 403);
  }

  @Test
  void consentTransitions_leaveAuditTrail() throws Exception {
    optIn("+15550006104", "servicing");
    mvc.perform(post("/v1/consent/opt-out").contentType(MediaType.APPLICATION_JSON).content(
        "{\"phoneE164\":\"+15550006104\",\"topic\":\"servicing\",\"source\":\"keyword\",\"actor\":\"twilio\"}"))
        .andExpect(status().isOk());

    assertThat(events.findByPhoneE164AndTopicOrderByCreatedAtAsc("+15550006104", "servicing"))
        .extracting("oldStatus", "newStatus")
        .containsExactly(tuple(null, "opted_in"), tuple("opted_in", "opted_out"));
  }
}
