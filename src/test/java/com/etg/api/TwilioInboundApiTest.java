package com.etg.api;

import static org.hamcrest.Matchers.*;
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

/** API contract for Twilio webhooks: form-encoded inbound + DLR, TwiML responses. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TwilioInboundApiTest {

  @Autowired MockMvc mvc;

  @Test
  void stop_optsOut_andReturnsUnsubscribeTwiml() throws Exception {
    mvc.perform(post("/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("From", "+15550004201").param("Body", "STOP"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("unsubscribed")));
    // marketing send must now be blocked for that number
    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON).content("""
            {"toE164":"+15550004201","topic":"marketing","body":"promo"}"""))
        .andExpect(status().isForbidden());
  }

  @Test
  void help_returnsHelpTwiml() throws Exception {
    mvc.perform(post("/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("From", "+15550004202").param("Body", "HELP"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Reply STOP")));
  }

  @Test
  void unknownBody_routesToAgentInbox() throws Exception {
    mvc.perform(post("/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("From", "+15550004203").param("Body", "what is my deductible?"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("agent will reply")));
  }

  @Test
  void dlr_accepted() throws Exception {
    mvc.perform(post("/twilio/dlr").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("MessageSid", "SM123").param("MessageStatus", "delivered"))
        .andExpect(status().isOk());
  }
}
