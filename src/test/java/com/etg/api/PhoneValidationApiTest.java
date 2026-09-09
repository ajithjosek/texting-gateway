package com.etg.api;

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

/** Phone validation contract on the send API and consent capture. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PhoneValidationApiTest {

  @Autowired MockMvc mvc;

  @Test
  void send_rejectsMalformedPhone_400() throws Exception {
    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON).content("""
            {"toE164":"not-a-phone","topic":"billing","body":"due"}"""))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON).content("""
            {"toE164":"4155552671","topic":"billing","body":"due"}"""))
        .andExpect(status().isBadRequest());
  }

  @Test
  void consent_rejectsMalformedPhone_400() throws Exception {
    mvc.perform(post("/v1/consent/opt-in").contentType(MediaType.APPLICATION_JSON).content(
        "{\"phoneE164\":\"+1555\",\"topic\":\"servicing\",\"source\":\"api\",\"proofTextVersion\":\"v1\"}"))
        .andExpect(status().isBadRequest());
  }
}
