package com.etg.salesforce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Live Salesforce REST client (username-password OAuth flow). Constructed by SfConfig
 * only when {@code etg.salesforce.enabled=true}. Token cached; single re-auth retry on 401.
 */
public class RestSalesforceClient implements SalesforceClient {
  private static final Logger log = LoggerFactory.getLogger(RestSalesforceClient.class);
  static final String API_VERSION = "v59.0";

  private final RestTemplate http;
  private final ObjectMapper json = new ObjectMapper();
  private final String loginUrl;
  private final String clientId;
  private final String clientSecret;
  private final String username;
  private final String password;

  private volatile String accessToken;
  private volatile String instanceUrl;

  public RestSalesforceClient(RestTemplate http, String loginUrl, String clientId,
                              String clientSecret, String username, String password) {
    this.http = http;
    this.loginUrl = loginUrl;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.username = username;
    this.password = password;
  }

  @Override
  public Optional<SfContact> findByPhone(String phoneE164) {
    JsonNode res = query(
        "SELECT Id,Phone FROM Contact WHERE Phone = '" + phoneE164.replace("'", "") + "' LIMIT 1");
    JsonNode records = res.path("records");
    if (records.isArray() && !records.isEmpty()) {
      return Optional.of(new SfContact(records.get(0).path("Id").asText(), phoneE164));
    }
    return Optional.empty();
  }

  @Override
  public SfContact upsertContact(String phoneE164, String topic) {
    Optional<SfContact> existing = findByPhone(phoneE164);
    if (existing.isPresent()) return existing.get();
    JsonNode res = post("/sobjects/Contact", Map.of(
        "Phone", phoneE164, "LeadSource", "ETG", "Description", "ETG topic: " + topic));
    return new SfContact(res.path("id").asText(), phoneE164);
  }

  @Override
  public void logTask(String phoneE164, String subject, String description) {
    String whoId = upsertContact(phoneE164, "sync").id();
    post("/sobjects/Task", Map.of(
        "WhoId", whoId, "Subject", subject, "Description", description, "Status", "Completed"));
  }

  private void ensureAuth() {
    if (accessToken != null) return;
    synchronized (this) {
      if (accessToken != null) return;
      var form = new LinkedMultiValueMap<String, String>();
      form.add("grant_type", "password");
      form.add("client_id", clientId);
      form.add("client_secret", clientSecret);
      form.add("username", username);
      form.add("password", password);
      var headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
      try {
        JsonNode res = json.readTree(http.postForObject(loginUrl + "/services/oauth2/token",
            new HttpEntity<>(form, headers), String.class));
        accessToken = res.path("access_token").asText();
        instanceUrl = res.path("instance_url").asText();
        if (accessToken.isBlank() || instanceUrl.isBlank()) {
          throw new IllegalStateException("Salesforce auth returned no token");
        }
      } catch (Exception e) {
        accessToken = null;
        throw new IllegalStateException("Salesforce auth failed", e);
      }
    }
  }

  private JsonNode query(String soql) {
    ensureAuth();
    java.net.URI uri = org.springframework.web.util.UriComponentsBuilder
        .fromUriString(instanceUrl + "/services/data/" + API_VERSION + "/query")
        .queryParam("q", soql).encode().build().toUri();
    return exchange(uri, HttpMethod.GET, null, true);
  }

  private JsonNode post(String path, Object body) {
    ensureAuth();
    java.net.URI uri = java.net.URI.create(instanceUrl + "/services/data/" + API_VERSION + path);
    return exchange(uri, HttpMethod.POST, body, true);
  }

  private JsonNode exchange(java.net.URI uri, HttpMethod method, Object body, boolean retry) {
    var headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    try {
      String payload = http.exchange(uri, method, new HttpEntity<>(body, headers), String.class).getBody();
      return json.readTree(payload == null ? "{}" : payload);
    } catch (HttpClientErrorException.Unauthorized e) {
      if (!retry) throw e;
      log.info("Salesforce token expired, re-authenticating once");
      accessToken = null;
      ensureAuth();
      return exchange(uri, method, body, false);
    } catch (Exception e) {
      throw new IllegalStateException("Salesforce call failed: " + uri, e);
    }
  }
}
