package com.etg.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.etg.inbox.ConversationRepository;
import com.etg.salesforce.StubSalesforceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** CRM write-through contract against the default stub client. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SalesforceSyncApiTest {

  @Autowired MockMvc mvc;
  @Autowired StubSalesforceClient salesforce;
  @Autowired ConversationRepository conversations;

  @Test
  void send_upsertsContact_andLogsTask() throws Exception {
    mvc.perform(post("/v1/consent/opt-in").contentType(MediaType.APPLICATION_JSON).content(
        "{\"phoneE164\":\"+15550011101\",\"topic\":\"servicing\",\"source\":\"api\",\"proofTextVersion\":\"v1\"}"))
        .andExpect(status().isOk());
    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON).content(
        "{\"toE164\":\"+15550011101\",\"topic\":\"servicing\",\"body\":\"hi\",\"idempotencyKey\":\"sf-1\"}"))
        .andExpect(status().isOk());

    assertThat(salesforce.findByPhone("+15550011101")).isPresent();
    assertThat(salesforce.tasks()).extracting(StubSalesforceClient.Task::subject)
        .anyMatch(s -> s.contains("servicing"));
  }

  @Test
  void inbound_upsertsContact() throws Exception {
    mvc.perform(post("/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("From", "+15550011102").param("Body", "hello"))
        .andExpect(status().isOk());
    assertThat(salesforce.findByPhone("+15550011102")).isPresent();
  }

  @Test
  void close_pushesTranscriptTask() throws Exception {
    mvc.perform(post("/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("From", "+15550011103").param("Body", "need help"))
        .andExpect(status().isOk());
    Long id = conversations
        .findFirstByCustomerPhoneAndTopicAndStatusNot("+15550011103", "servicing", "CLOSED")
        .orElseThrow().getId();
    mvc.perform(post("/v1/inbox/" + id + "/close")).andExpect(status().isOk());

    assertThat(salesforce.tasks()).extracting(StubSalesforceClient.Task::subject)
        .anyMatch(s -> s.contains("transcript"));
    assertThat(salesforce.tasks()).extracting(StubSalesforceClient.Task::description)
        .anyMatch(d -> d.contains("need help"));
  }

  @Test
  void status_reportsStubModeByDefault() throws Exception {
    mvc.perform(get("/v1/hub/salesforce/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("stub"))
        .andExpect(jsonPath("$.enabled").value("false"));
  }
}
