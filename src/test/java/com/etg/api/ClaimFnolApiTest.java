package com.etg.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** FNOL flow contract against the default stub adapter: CLAIM → policy → date → type → number. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClaimFnolApiTest {

  @Autowired MockMvc mvc;

  private void text(String from, String body) throws Exception {
    mvc.perform(post("/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("From", from).param("Body", body))
        .andExpect(status().isOk());
  }

  private void expectReply(String from, String body, String contains) throws Exception {
    mvc.perform(post("/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("From", from).param("Body", body))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(contains)));
  }

  @Test
  void fullFlow_filesClaim() throws Exception {
    String phone = "+15550013101";
    expectReply(phone, "CLAIM", "policy number");
    expectReply(phone, "POL-77", "YYYY-MM-DD");
    expectReply(phone, "not a date", "didn't parse");
    expectReply(phone, "2026-05-30", "AUTO, HOME, or OTHER");
    expectReply(phone, "BOAT", "AUTO, HOME, or OTHER");
    expectReply(phone, "AUTO", "Your claim number is CLM-");
    // Session cleared: STATUS usage reply, not a flow reprompt.
    expectReply(phone, "STATUS", "STATUS CLM-1001");
  }

  @Test
  void cancel_abortsFlow() throws Exception {
    String phone = "+15550013102";
    expectReply(phone, "CLAIM", "policy number");
    expectReply(phone, "CANCEL", "cancelled");
    // Session gone: free text routes to agent inbox, not the flow.
    expectReply(phone, "POL-77", "agent will reply");
  }

  @Test
  void stop_winsOverActiveSession() throws Exception {
    String phone = "+15550013103";
    expectReply(phone, "CLAIM", "policy number");
    expectReply(phone, "STOP", "unsubscribed");
  }
}
