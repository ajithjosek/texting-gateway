package com.etg.claimcenter;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class RestClaimCenterClientTest {

  private MockRestServiceServer server;
  private RestClaimCenterClient client;

  @BeforeEach
  void setUp() {
    RestTemplate http = new RestTemplate();
    server = MockRestServiceServer.bindTo(http).build();
    client = new RestClaimCenterClient(http, "https://gw.example", "key-1");
  }

  @Test
  void statusOf_mapsFields() {
    server.expect(requestTo("https://gw.example/cc/rest/claims/CLM-1001"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(
            "{\"number\":\"CLM-1001\",\"status\":\"Open\",\"adjusterName\":\"R. Diaz\",\"eta\":\"Jun 10\"}",
            MediaType.APPLICATION_JSON));
    assertThat(client.statusOf("CLM-1001")).contains(
        new ClaimStatus("CLM-1001", "Open", "R. Diaz", "Jun 10"));
    server.verify();
  }

  @Test
  void statusOf_404_isEmpty() {
    server.expect(requestTo("https://gw.example/cc/rest/claims/CLM-9"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
    assertThat(client.statusOf("CLM-9")).isEmpty();
    server.verify();
  }

  @Test
  void slotsAndBooking_roundtrip() {
    server.expect(requestTo("https://gw.example/cc/rest/claims/CLM-1001/slots"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("[{\"id\":\"S1\",\"label\":\"Mon 9:00 AM ET\"}]",
            MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://gw.example/cc/rest/claims/CLM-1001/bookings"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("{\"confirmation\":\"BKG-7\"}", MediaType.APPLICATION_JSON));

    assertThat(client.slotsFor("CLM-1001")).extracting(AdjusterSlot::id).containsExactly("S1");
    assertThat(client.bookSlot("CLM-1001", "S1", "+15550012002"))
        .contains(new Booking("CLM-1001", "S1", "BKG-7"));
    server.verify();
  }
}
