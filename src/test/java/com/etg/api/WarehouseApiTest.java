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

/** Warehouse export contract: every domain write surfaces in the event stream. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WarehouseApiTest {

  @Autowired MockMvc mvc;

  @Test
  void stream_containsConsentMessageAndDeliveryEvents_withCursor() throws Exception {
    mvc.perform(post("/v1/consent/opt-in").contentType(MediaType.APPLICATION_JSON).content(
        "{\"phoneE164\":\"+15550016001\",\"topic\":\"servicing\",\"source\":\"api\",\"proofTextVersion\":\"v1\"}"))
        .andExpect(status().isOk());
    mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON).content(
        "{\"toE164\":\"+15550016001\",\"topic\":\"servicing\",\"body\":\"hi\",\"idempotencyKey\":\"wh-1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sid", not(emptyString())));
    String sid = com.jayway.jsonpath.JsonPath.read(
        mvc.perform(post("/v1/messages").contentType(MediaType.APPLICATION_JSON).content(
            "{\"toE164\":\"+15550016001\",\"topic\":\"servicing\",\"body\":\"hi\",\"idempotencyKey\":\"wh-1b\"}"))
            .andReturn().getResponse().getContentAsString(), "$.sid");
    mvc.perform(post("/twilio/dlr").contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("MessageSid", sid).param("MessageStatus", "delivered"))
        .andExpect(status().isOk());

    mvc.perform(get("/v1/exports/events").param("limit", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.events[*].eventType",
            hasItems("consent.updated", "message.created", "delivery.updated")))
        .andExpect(jsonPath("$.events[?(@.eventType=='consent.updated')].payload.newStatus",
            hasItem("opted_in")))
        .andExpect(jsonPath("$.events[?(@.eventType=='delivery.updated')].payload.status",
            hasItem("delivered")))
        .andExpect(jsonPath("$.nextCursor", greaterThan(0)));

    // Cursor pages forward: second page excludes the first event.
    int firstId = (Integer) com.jayway.jsonpath.JsonPath.read(
        mvc.perform(get("/v1/exports/events").param("limit", "100"))
            .andReturn().getResponse().getContentAsString(), "$.events[0].id");
    mvc.perform(get("/v1/exports/events").param("sinceId", String.valueOf(firstId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.events[*].id", everyItem(greaterThan(firstId))));
  }

  @Test
  void limit_isClamped() throws Exception {
    mvc.perform(get("/v1/exports/events").param("limit", "5000"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.events", hasSize(lessThanOrEqualTo(1000))));
  }
}
