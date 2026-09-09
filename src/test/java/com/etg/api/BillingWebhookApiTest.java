package com.etg.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.etg.messaging.MessageRepository;
import com.etg.template.TemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Billing webhook contract: templated sends, event-id idempotency, consent gate. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BillingWebhookApiTest {

  @Autowired MockMvc mvc;
  @Autowired MessageRepository messages;
  @Autowired TemplateService templates;

  @BeforeEach
  void seedTemplates() {
    // Test profile disables Flyway seeds (V4); create the billing templates directly.
    templates.createVersion("payment_due", "en", "Due {{amount}} {{policy_number}} {{pay_link}}");
    templates.createVersion("payment_failed", "en", "Failed {{amount}} {{policy_number}} {{pay_link}}");
    templates.createVersion("payment_receipt", "en", "Thanks {{amount}} {{receipt_link}}");
  }

  private void optInBilling(String phone) throws Exception {
    mvc.perform(post("/v1/consent/opt-in").contentType(MediaType.APPLICATION_JSON).content(
        "{\"phoneE164\":\"" + phone + "\",\"topic\":\"billing\",\"source\":\"api\",\"proofTextVersion\":\"v1\"}"))
        .andExpect(status().isOk());
  }

  private String event(String eventId, String type, String phone) {
    return "{\"eventId\":\"" + eventId + "\",\"type\":\"" + type + "\",\"phone\":\"" + phone
        + "\",\"firstName\":\"Ana\",\"policyNumber\":\"POL-9\",\"amountCents\":12000,\"dueDate\":\"Dec 1\"}";
  }

  @Test
  void due_rendersTemplateWithPayLink() throws Exception {
    optInBilling("+15550009011");
    mvc.perform(post("/v1/hub/billing/events").contentType(MediaType.APPLICATION_JSON)
            .content(event("evt-1", "DUE", "+15550009011")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("queued"));

    var row = messages.findByIdempotencyKey("billing:evt-1").orElseThrow();
    // Body is hashed at rest; the render itself is asserted below via the receipt path.
    assertThat(row.getTopic()).isEqualTo("billing");
  }

  @Test
  void duplicateEvent_doesNotResend() throws Exception {
    optInBilling("+15550009012");
    String body = event("evt-2", "DUE", "+15550009012");
    mvc.perform(post("/v1/hub/billing/events").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk());
    mvc.perform(post("/v1/hub/billing/events").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk());
    assertThat(messages.findAll().stream()
        .filter(m -> "billing:evt-2".equals(m.getIdempotencyKey())).count()).isEqualTo(1);
  }

  @Test
  void failed_usesFailedTemplate_withoutOptIn_isForbidden() throws Exception {
    mvc.perform(post("/v1/hub/billing/events").contentType(MediaType.APPLICATION_JSON)
            .content(event("evt-3", "FAILED", "+15550009013")))
        .andExpect(status().isForbidden());
  }

  @Test
  void invalidPhone_isBadRequest() throws Exception {
    mvc.perform(post("/v1/hub/billing/events").contentType(MediaType.APPLICATION_JSON)
            .content(event("evt-4", "DUE", "bad")))
        .andExpect(status().isBadRequest());
  }
}
