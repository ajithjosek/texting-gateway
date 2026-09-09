package com.etg.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.etg.messaging.MessageRepository;
import com.etg.policycenter.RenewalCandidate;
import com.etg.policycenter.StubPolicyCenterClient;
import com.etg.scheduler.RenewalScheduler;
import com.etg.template.TemplateService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Renewal run contract: templated idempotent sends, consent gating, manual trigger. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RenewalSchedulerApiTest {

  @Autowired MockMvc mvc;
  @Autowired RenewalScheduler scheduler;
  @Autowired StubPolicyCenterClient policies;
  @Autowired TemplateService templates;
  @Autowired MessageRepository messages;

  @BeforeEach
  void seed() {
    templates.createVersion("renewal_reminder", "en",
        "Hi {{first_name}}, policy {{policy_number}} renews {{due_date}}. E-sign: {{esign_link}}");
  }

  private void optInServicing(String phone) throws Exception {
    mvc.perform(post("/v1/consent/opt-in").contentType(MediaType.APPLICATION_JSON).content(
        "{\"phoneE164\":\"" + phone + "\",\"topic\":\"servicing\",\"source\":\"api\",\"proofTextVersion\":\"v1\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void run_sendsOncePerCycle_skipsOthers() throws Exception {
    optInServicing("+15550014101");
    policies.addCandidate(new RenewalCandidate("POL-R1", "+15550014101", "Ana",
        LocalDate.now().plusDays(20), "P&C"));
    policies.addCandidate(new RenewalCandidate("POL-R2", "+15550014102", "Bo",
        LocalDate.now().plusDays(10), "P&C")); // no consent -> skipped
    policies.addCandidate(new RenewalCandidate("POL-R3", "+15550014101", "Ana",
        LocalDate.now().plusDays(90), "P&C")); // out of window -> ignored

    RenewalScheduler.RunResult r = scheduler.runOnce();
    assertThat(r.sent()).isEqualTo(1);
    assertThat(r.skipped()).isEqualTo(1);
    assertThat(messages.findByIdempotencyKey(
        "renewal:POL-R1:" + LocalDate.now().plusDays(20))).isPresent();

    // Second run: same cycle, no duplicate row.
    scheduler.runOnce();
    assertThat(messages.findAll().stream()
        .filter(m -> m.getIdempotencyKey().startsWith("renewal:POL-R1")).count()).isEqualTo(1);
  }

  @Test
  void runEndpoint_triggersManually() throws Exception {
    optInServicing("+15550014103");
    policies.addCandidate(new RenewalCandidate("POL-R4", "+15550014103", "Cy",
        LocalDate.now().plusDays(5), "Life"));
    mvc.perform(post("/v1/hub/policycenter/run"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sent").value(1));
  }

  @Test
  void status_reportsStubByDefault() throws Exception {
    mvc.perform(get("/v1/hub/policycenter/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("stub"));
  }
}
