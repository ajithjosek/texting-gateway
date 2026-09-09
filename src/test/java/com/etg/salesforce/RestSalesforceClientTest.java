package com.etg.salesforce;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class RestSalesforceClientTest {

  private RestTemplate http;
  private MockRestServiceServer server;
  private RestSalesforceClient client;

  @BeforeEach
  void setUp() {
    http = new RestTemplate();
    server = MockRestServiceServer.bindTo(http).build();
    client = new RestSalesforceClient(http, "https://login.example", "cid", "csec", "u", "p");
  }

  private void expectAuth() {
    server.expect(requestTo("https://login.example/services/oauth2/token"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(
            "{\"access_token\":\"tok\",\"instance_url\":\"https://inst.example\"}",
            MediaType.APPLICATION_JSON));
  }

  private void expectQuery(String phone, String responseJson) {
    server.expect(requestTo(containsString("/services/data/v59.0/query")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(queryParam("q", allOf(containsString("Phone"), containsString(phone))))
        .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));
  }

  @Test
  void findByPhone_returnsContactWhenPresent() {
    expectAuth();
    expectQuery("+15550011011", "{\"records\":[{\"Id\":\"003X\",\"Phone\":\"+15550011011\"}]}");

    assertThat(client.findByPhone("+15550011011")).contains(new SfContact("003X", "+15550011011"));
    server.verify();
  }

  @Test
  void upsertContact_createsWhenAbsent() {
    expectAuth();
    expectQuery("+15550011012", "{\"records\":[]}");
    server.expect(requestTo("https://inst.example/services/data/v59.0/sobjects/Contact"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"id\":\"003Y\"}", MediaType.APPLICATION_JSON));

    assertThat(client.upsertContact("+15550011012", "servicing").id()).isEqualTo("003Y");
    server.verify();
  }

  @Test
  void tokenIsCached_secondCallSkipsAuth() {
    expectAuth();
    expectQuery("+15550011013", "{\"records\":[]}");
    expectQuery("+15550011013", "{\"records\":[]}");

    client.findByPhone("+15550011013");
    client.findByPhone("+15550011013");
    server.verify();
  }
}
