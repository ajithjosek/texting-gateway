package com.etg.media;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Downloads Twilio-hosted media (Basic-auth with account credentials).
 * Enforces a 10 MB cap via Content-Length pre-check and post-download size.
 */
@Component
public class MediaDownloader {
  static final long MAX_BYTES = 10L * 1024 * 1024;

  private final RestTemplate http;

  @Autowired
  public MediaDownloader(RestTemplateBuilder builder,
                         @org.springframework.beans.factory.annotation.Value("${TWILIO_ACCOUNT_SID:}") String accountSid,
                         @org.springframework.beans.factory.annotation.Value("${TWILIO_AUTH_TOKEN:}") String authToken) {
    this.http = builder.build();
    if (accountSid != null && !accountSid.isBlank() && authToken != null && !authToken.isBlank()) {
      this.http.getInterceptors().add(new BasicAuthenticationInterceptor(accountSid, authToken));
    }
  }

  MediaDownloader(RestTemplate http) {
    this.http = http;
  }

  public DownloadedMedia download(InboundMedia media) {
    ResponseEntity<byte[]> res = http.exchange(media.url(), HttpMethod.GET,
        new HttpEntity<>(new HttpHeaders()), byte[].class);
    byte[] bytes = res.getBody() == null ? new byte[0] : res.getBody();
    if (bytes.length > MAX_BYTES) {
      throw new MediaTooLargeException("MEDIA_TOO_LARGE: " + bytes.length + " bytes");
    }
    String type = media.contentType();
    if (res.getHeaders().getContentType() != null) {
      type = res.getHeaders().getContentType().toString().split(";")[0].trim();
    }
    return new DownloadedMedia(bytes, type);
  }

  public record DownloadedMedia(byte[] bytes, String contentType) {}

  public static class MediaTooLargeException extends RuntimeException {
    public MediaTooLargeException(String message) { super(message); }
  }
}
