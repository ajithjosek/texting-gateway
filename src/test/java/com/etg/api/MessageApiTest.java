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

/** API contract for POST /v1/messages: validation, consent gate, idempotency echo. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MessageApiTest {

  @Autowired MockMvc mvc;

  private void optIn(String phone, String topic) throws Exception {
    mvc.perform(post("/v1/consent/opt-in").contentType(MediaType.APPLICATION_JSON).content(
        "{\"phoneE164\":\"" + phone + "\",\"topic\":\"" + topic
            + "\",\"source\":\"api\",\"proofTextVersion\":\"v1\",\"actor\":\"test\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void send_blockedWithoutConsent_403() throws Exception {
    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON).content("""
            {"toE164":"+15550004101","topic":"billing","body":"due"}"""))
        .andExpect(status().isForbidden());
  }

  @Test
  void send_allowedAfterOptIn_echoesBodyKey_andHeaderKey() throws Exception {
    optIn("+15550004102", "billing");
    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON).content("""
            {"toE164":"+15550004102","topic":"billing","body":"due","idempotencyKey":"body-key-1"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.idempotencyKey", is("body-key-1")))
        .andExpect(jsonPath("$.sid", not(emptyString())));
    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", "hdr-key-2")
            .content("""
                {"toE164":"+15550004102","topic":"billing","body":"due"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.idempotencyKey", is("hdr-key-2")));
  }

  @Test
  void send_rejectsBlankFields_400() throws Exception {
    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON).content("""
            {"toE164":"","topic":"billing","body":"due"}"""))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON).content("""
            {"toE164":"+15550004103","topic":"","body":"due"}"""))
        .andExpect(status().isBadRequest());
  }
}
