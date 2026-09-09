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

/** API contract for consent: opt-in -> gated send passes; opt-out -> gated send blocked. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConsentApiTest {

  @Autowired MockMvc mvc;

  @Test
  void optIn_thenOptOut_roundtrip() throws Exception {
    String body = """
        {"phoneE164":"+15550004001","topic":"servicing","source":"api","proofTextVersion":"v1","actor":"test"}""";
    mvc.perform(post("/v1/consent/opt-in").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("opted_in")));

    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"toE164":"+15550004001","topic":"servicing","body":"hello"}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("queued")));

    mvc.perform(post("/v1/consent/opt-out").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("opted_out")));

    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"toE164":"+15550004001","topic":"servicing","body":"hello"}"""))
        .andExpect(status().isForbidden());
  }

  @Test
  void consent_isPerTopic_marketingOptOutDoesNotBlockServicing() throws Exception {
    mvc.perform(post("/v1/consent/opt-in").contentType(MediaType.APPLICATION_JSON).content("""
            {"phoneE164":"+15550004002","topic":"servicing","source":"api","proofTextVersion":"v1","actor":"test"}"""))
        .andExpect(status().isOk());
    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON).content("""
            {"toE164":"+15550004002","topic":"marketing","body":"promo"}"""))
        .andExpect(status().isForbidden());
    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON).content("""
            {"toE164":"+15550004002","topic":"servicing","body":"renewal"}"""))
        .andExpect(status().isOk());
  }
}
