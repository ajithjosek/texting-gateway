package com.etg.policycenter;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class RestPolicyCenterClientTest {

  @Test
  void renewals_mapsFields() {
    RestTemplate http = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
    var client = new RestPolicyCenterClient(http, "https://pc.example", "key-1");

    server.expect(requestTo("https://pc.example/pc/rest/policies?renewalBefore=2026-07-01"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(
            "[{\"policyNumber\":\"POL-1\",\"phone\":\"+15550014201\",\"firstName\":\"Ana\","
                + "\"dueDate\":\"2026-06-21\",\"lob\":\"P&C\"}]",
            MediaType.APPLICATION_JSON));

    assertThat(client.renewalsDueBefore(LocalDate.parse("2026-07-01"))).containsExactly(
        new RenewalCandidate("POL-1", "+15550014201", "Ana", LocalDate.parse("2026-06-21"), "P&C"));
    server.verify();
  }

  @Test
  void outage_returnsEmpty() {
    RestTemplate http = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
    var client = new RestPolicyCenterClient(http, "https://pc.example", "key-1");
    server.expect(requestTo("https://pc.example/pc/rest/policies?renewalBefore=2026-07-01"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withServerError());
    assertThat(client.renewalsDueBefore(LocalDate.parse("2026-07-01"))).isEmpty();
    server.verify();
  }
}
