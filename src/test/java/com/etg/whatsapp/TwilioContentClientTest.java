package com.etg.whatsapp;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

class TwilioContentClientTest {

  private TwilioContentClient client;
  private MockRestServiceServer server;

  private void withCreds() {
    client = new TwilioContentClient(new RestTemplateBuilder(), "https://content.example", "AC1", "tok");
    server = MockRestServiceServer.bindTo(client.http()).build();
  }

  @Test
  void approval_returnsLowercasedStatus() {
    withCreds();
    server.expect(requestTo("https://content.example/v2/Contents/HX1/ApprovalRequests/whatsapp"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"status\":\"approved\"}", MediaType.APPLICATION_JSON));
    assertThat(client.whatsappApproval("HX1")).contains("approved");
    server.verify();
  }

  @Test
  void error_isEmpty_failOpen() {
    withCreds();
    server.expect(requestTo("https://content.example/v2/Contents/HX2/ApprovalRequests/whatsapp"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withServerError());
    assertThat(client.whatsappApproval("HX2")).isEmpty();
    server.verify();
  }

  @Test
  void noCredentials_isEmpty_withoutHttp() {
    client = new TwilioContentClient(new RestTemplateBuilder(), "https://content.example", "", "");
    assertThat(client.whatsappApproval("HX1")).isEmpty();
  }
}
