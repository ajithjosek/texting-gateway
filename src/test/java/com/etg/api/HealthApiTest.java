package com.etg.api;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthApiTest {

  @Autowired MockMvc mvc;

  @Test
  void actuatorHealth_isUp() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("UP")));
  }

  @Test
  void root_returnsServiceDescriptor() throws Exception {
    mvc.perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.service", is("enterprise-texting-gateway")));
  }
}
