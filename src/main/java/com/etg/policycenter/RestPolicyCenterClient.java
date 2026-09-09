package com.etg.policycenter;

import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

/** Live PolicyCenter REST client (Bearer API key). Fail-open: errors yield no candidates. */
public class RestPolicyCenterClient implements PolicyCenterClient {
  private static final Logger log = LoggerFactory.getLogger(RestPolicyCenterClient.class);

  private final RestTemplate http;
  private final String baseUrl;
  private final String apiKey;

  public RestPolicyCenterClient(RestTemplate http, String baseUrl, String apiKey) {
    this.http = http;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.apiKey = apiKey;
  }

  @Override
  public List<RenewalCandidate> renewalsDueBefore(LocalDate date) {
    try {
      var headers = new HttpHeaders();
      headers.setBearerAuth(apiKey);
      headers.setAccept(List.of(MediaType.APPLICATION_JSON));
      PolicyJson[] res = http.exchange(baseUrl + "/pc/rest/policies?renewalBefore=" + date,
          HttpMethod.GET, new HttpEntity<>(headers), PolicyJson[].class).getBody();
      if (res == null) return List.of();
      return java.util.Arrays.stream(res)
          .map(p -> new RenewalCandidate(p.policyNumber(), p.phone(), p.firstName(),
              LocalDate.parse(p.dueDate()), p.lob()))
          .toList();
    } catch (Exception e) {
      log.warn("PolicyCenter renewal fetch failed: {}", e.getMessage());
      return List.of();
    }
  }

  public record PolicyJson(String policyNumber, String phone, String firstName,
                           String dueDate, String lob) {}
}
