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

/** Claim keyword contract against the default stub adapter. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClaimLookupApiTest {

  @Autowired MockMvc mvc;

  private void inbound(String from, String body) throws Exception {
    mvc.perform(post("/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("From", from).param("Body", body))
        .andExpect(status().isOk());
  }

  @Test
  void status_found_returnsDetails() throws Exception {
    mvc.perform(post("/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("From", "+15550012101").param("Body", "STATUS CLM-1001"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("R. Diaz")));
  }

  @Test
  void status_missingNumber_returnsUsage() throws Exception {
    mvc.perform(post("/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("From", "+15550012102").param("Body", "STATUS"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("STATUS CLM-1001")));
  }

  @Test
  void status_unknown_returnsNotFound() throws Exception {
    mvc.perform(post("/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("From", "+15550012103").param("Body", "STATUS CLM-9999"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("couldn't find")));
  }

  @Test
  void schedule_thenBook_endToEnd() throws Exception {
    mvc.perform(post("/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("From", "+15550012104").param("Body", "SCHEDULE CLM-1001"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("S1")))
        .andExpect(content().string(containsString("BOOK CLM-1001 S1")));
    mvc.perform(post("/twilio/inbound").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("From", "+15550012104").param("Body", "BOOK CLM-1001 S1"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Booked!")));
  }

  @Test
  void guidewire_status_reportsStubByDefault() throws Exception {
    mvc.perform(get("/v1/hub/guidewire/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("stub"));
  }
}
