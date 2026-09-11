package com.etg.claimcenter;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

/**
 * Live Guidewire ClaimCenter REST client. Constructed by ClaimCenterConfig
 * only when {@code etg.guidewire.enabled=true}. Claim JSON shapes follow the
 * ClaimCenter Cloud API ({@code /cc/rest/claims}).
 */
public class RestClaimCenterClient implements ClaimCenterClient {
  private static final Logger log = LoggerFactory.getLogger(RestClaimCenterClient.class);

  private final RestTemplate http;
  private final String baseUrl;
  private final String apiKey;

  public RestClaimCenterClient(RestTemplate http, String baseUrl, String apiKey) {
    this.http = http;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.apiKey = apiKey;
  }

  @Override
  public Optional<ClaimStatus> statusOf(String claimNumber) {
    try {
      var res = get("/cc/rest/claims/" + claimNumber, ClaimJson.class);
      if (res == null || res.number() == null) return Optional.empty();
      return Optional.of(new ClaimStatus(res.number(), res.status(), res.adjusterName(), res.eta()));
    } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
      return Optional.empty();
    } catch (Exception e) {
      log.warn("ClaimCenter status lookup failed for {}: {}", claimNumber, e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public List<AdjusterSlot> slotsFor(String claimNumber) {
    try {
      SlotJson[] res = get("/cc/rest/claims/" + claimNumber + "/slots", SlotJson[].class);
      if (res == null) return List.of();
      return java.util.Arrays.stream(res)
          .map(s -> new AdjusterSlot(s.id(), claimNumber.toUpperCase(), s.label()))
          .toList();
    } catch (Exception e) {
      log.warn("ClaimCenter slot lookup failed for {}: {}", claimNumber, e.getMessage());
      return List.of();
    }
  }

  @Override
  public Optional<Booking> bookSlot(String claimNumber, String slotId, String phoneE164) {
    try {
      var res = post("/cc/rest/claims/" + claimNumber + "/bookings",
          java.util.Map.of("slotId", slotId, "phone", phoneE164), BookingJson.class);
      if (res == null || res.confirmation() == null) return Optional.empty();
      return Optional.of(new Booking(claimNumber.toUpperCase(), slotId.toUpperCase(), res.confirmation()));
    } catch (Exception e) {
      log.warn("ClaimCenter booking failed for {}/{}: {}", claimNumber, slotId, e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public Optional<FnolResult> createFnol(FnolRequest request) {
    try {
      var res = post("/cc/rest/claims/fnol", java.util.Map.of(
          "phone", request.phoneE164(), "policyNumber", request.policyNumber(),
          "lossDate", request.lossDate(), "lossType", request.lossType()), FnolJson.class);
      if (res == null || res.number() == null) return Optional.empty();
      return Optional.of(new FnolResult(res.number()));
    } catch (Exception e) {
      log.warn("ClaimCenter FNOL failed for {}: {}", request.policyNumber(), e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public boolean attachPhoto(String claimNumber, String mediaRef, String contentType) {
    try {
      post("/cc/rest/claims/" + claimNumber + "/attachments",
          java.util.Map.of("mediaRef", mediaRef, "contentType", contentType), JsonNode.class);
      return true;
    } catch (Exception e) {
      log.warn("ClaimCenter attach failed for {}: {}", claimNumber, e.getMessage());
      return false;
    }
  }

  private <T> T get(String path, Class<T> type) {
    return http.exchange(baseUrl + path, HttpMethod.GET, new HttpEntity<>(auth()), type).getBody();
  }

  private <T> T post(String path, Object body, Class<T> type) {
    return http.exchange(baseUrl + path, HttpMethod.POST, new HttpEntity<>(body, auth()), type).getBody();
  }

  private HttpHeaders auth() {
    var h = new HttpHeaders();
    h.setBearerAuth(apiKey);
    h.setContentType(MediaType.APPLICATION_JSON);
    return h;
  }

  public record ClaimJson(String number, String status, String adjusterName, String eta) {}
  public record SlotJson(String id, String label) {}
  public record BookingJson(String confirmation) {}
  public record FnolJson(String number) {}
}
