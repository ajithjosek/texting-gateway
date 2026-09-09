package com.etg.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.etg.links.LinkSigner;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LinkApiTest {

  @Autowired MockMvc mvc;
  @Autowired LinkSigner links;

  @Test
  void verify_roundtrip_ok() throws Exception {
    String token = links.sign("pay", "POL-9", Duration.ofDays(7));
    mvc.perform(get("/v1/links/verify").param("purpose", "pay").param("token", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.refId").value("POL-9"))
        .andExpect(jsonPath("$.valid").value("true"));
  }

  @Test
  void verify_tampered_gone() throws Exception {
    String token = links.sign("pay", "POL-9", Duration.ofDays(7)) + "tamper";
    mvc.perform(get("/v1/links/verify").param("purpose", "pay").param("token", token))
        .andExpect(status().isGone());
  }
}
