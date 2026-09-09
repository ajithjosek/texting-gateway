package com.etg.api;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.etg.messaging.MessageRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** Campaign lifecycle: draft -> legal approval (four-eyes) -> governed launch. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CampaignApiTest {

  @Autowired MockMvc mvc;
  @Autowired MessageRepository messages;
  @MockBean Clock clock;

  @BeforeEach
  void daytime() {
    // Marketing sends are quiet-hour/cap governed: pin 10:00 America/New_York.
    Instant now = Instant.parse("2026-06-01T14:00:00Z");
    lenient().when(clock.instant()).thenReturn(now);
    lenient().when(clock.withZone(any(ZoneId.class)))
        .thenAnswer(i -> Clock.fixed(now, i.getArgument(0)));
  }

  private void optInMarketing(String phone) throws Exception {
    mvc.perform(post("/v1/consent/opt-in").contentType(MediaType.APPLICATION_JSON).content(
        "{\"phoneE164\":\"" + phone + "\",\"topic\":\"marketing\",\"source\":\"api\",\"proofTextVersion\":\"v1\"}"))
        .andExpect(status().isOk());
  }

  private void seedPromo() throws Exception {
    mvc.perform(post("/v1/templates").contentType(MediaType.APPLICATION_JSON).content("""
            {"key":"promo","locale":"en","body":"Deal: {{offer}} Reply STOP to opt out."}"""))
        .andExpect(status().isCreated());
  }

  private long create(String name, String createdBy, String... phones) throws Exception {
    StringBuilder sb = new StringBuilder("{\"name\":\"" + name
        + "\",\"templateKey\":\"promo\",\"locale\":\"en\",\"vars\":{\"offer\":\"20off\"},\"phones\":[");
    for (int i = 0; i < phones.length; i++) {
      if (i > 0) sb.append(',');
      sb.append('"').append(phones[i]).append('"');
    }
    sb.append("],\"createdBy\":\"").append(createdBy).append("\"}");
    MvcResult res = mvc.perform(post("/v1/campaigns").contentType(MediaType.APPLICATION_JSON)
            .content(sb.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andReturn();
    return ((Number) com.jayway.jsonpath.JsonPath.read(res.getResponse().getContentAsString(), "$.id"))
        .longValue();
  }

  private void submit(long id) throws Exception {
    mvc.perform(post("/v1/campaigns/" + id + "/submit")).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  void fullLifecycle_sendsToConsented_skipsOthers() throws Exception {
    seedPromo();
    optInMarketing("+15550015001");
    optInMarketing("+15550015002");
    long id = create("summer", "marketer", "+15550015001", "+15550015002", "+15550015003");
    submit(id);
    mvc.perform(post("/v1/campaigns/" + id + "/approve").contentType(MediaType.APPLICATION_JSON)
            .content("{\"approver\":\"legal\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    mvc.perform(post("/v1/campaigns/" + id + "/launch")).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"))
        .andExpect(jsonPath("$.sentCount").value(2))
        .andExpect(jsonPath("$.skippedCount").value(1));

    assertThat(messages.findByIdempotencyKey("campaign:" + id + ":+15550015001")).isPresent();
    assertThat(messages.findByIdempotencyKey("campaign:" + id + ":+15550015003")).isEmpty();
  }

  @Test
  void launch_requiresApproval_and_rejectBlocks() throws Exception {
    seedPromo();
    long id = create("early", "marketer", "+15550015011");
    mvc.perform(post("/v1/campaigns/" + id + "/launch")).andExpect(status().isConflict());
    submit(id);
    mvc.perform(post("/v1/campaigns/" + id + "/reject").contentType(MediaType.APPLICATION_JSON)
            .content("{\"approver\":\"legal\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));
    mvc.perform(post("/v1/campaigns/" + id + "/launch")).andExpect(status().isConflict());
  }

  @Test
  void selfApproval_rejected_fourEyes() throws Exception {
    seedPromo();
    long id = create("self", "marketer", "+15550015021");
    submit(id);
    mvc.perform(post("/v1/campaigns/" + id + "/approve").contentType(MediaType.APPLICATION_JSON)
            .content("{\"approver\":\"marketer\"}"))
        .andExpect(status().isUnprocessableEntity());
  }
}
