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

/** Template lifecycle contract: versioning + render + locale fallback + 404. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TemplateApiTest {

  @Autowired MockMvc mvc;

  @Test
  void create_versions_deactivatePrevious() throws Exception {
    mvc.perform(post("/v1/templates").contentType(MediaType.APPLICATION_JSON).content("""
            {"key":"welcome","locale":"en","body":"Hi {{first_name}} v1"}"""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version", is(1)))
        .andExpect(jsonPath("$.active", is(true)));
    mvc.perform(post("/v1/templates").contentType(MediaType.APPLICATION_JSON).content("""
            {"key":"welcome","locale":"en","body":"Hi {{first_name}} v2"}"""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version", is(2)));

    mvc.perform(post("/v1/templates/render").contentType(MediaType.APPLICATION_JSON).content("""
            {"key":"welcome","locale":"en","vars":{"first_name":"Bo"}}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body", is("Hi Bo v2")));
  }

  @Test
  void render_missingKey_404_and_localeFallback() throws Exception {
    mvc.perform(post("/v1/templates/render").contentType(MediaType.APPLICATION_JSON).content("""
            {"key":"nope","locale":"en","vars":{}}"""))
        .andExpect(status().isNotFound());

    mvc.perform(post("/v1/templates").contentType(MediaType.APPLICATION_JSON).content("""
            {"key":"fallback","locale":"en","body":"Hello {{first_name}}"}"""))
        .andExpect(status().isCreated());
    mvc.perform(post("/v1/templates/render").contentType(MediaType.APPLICATION_JSON).content("""
            {"key":"fallback","locale":"es","vars":{"first_name":"Ana"}}"""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body", is("Hello Ana")));
  }
}
