package com.etg.links;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * HMAC-signed, expiring, purpose-bound tokens for pay and e-sign links.
 * Format: base64url(refId).expiryEpoch.hex(hmac(secret, purpose.refId.expiry)).
 * No server-side state — verification is pure crypto + clock.
 */
@Service
public class LinkSigner {
  private final Clock clock;
  private final String secret;

  public LinkSigner(Clock clock, @Value("${etg.link-secret:dev-secret-change-me}") String secret) {
    this.clock = clock;
    this.secret = secret;
  }

  public String sign(String purpose, String refId, Duration ttl) {
    long expiry = Instant.now(clock).plus(ttl).getEpochSecond();
    String ref = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(refId.getBytes(StandardCharsets.UTF_8));
    return ref + "." + expiry + "." + hmac(purpose, refId, expiry);
  }

  public String verify(String purpose, String token) {
    String[] parts = token == null ? new String[0] : token.split("\\.");
    if (parts.length != 3) throw new InvalidLinkException("LINK_MALFORMED");
    String refId;
    try {
      refId = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new InvalidLinkException("LINK_MALFORMED_ref");
    }
    long expiry;
    try {
      expiry = Long.parseLong(parts[1]);
    } catch (NumberFormatException e) {
      throw new InvalidLinkException("LINK_MALFORMED_expiry");
    }
    if (!MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.UTF_8),
        hmac(purpose, refId, expiry).getBytes(StandardCharsets.UTF_8))) {
      throw new InvalidLinkException("LINK_INVALID_signature");
    }
    if (Instant.now(clock).getEpochSecond() > expiry) {
      throw new InvalidLinkException("LINK_EXPIRED");
    }
    return refId;
  }

  private String hmac(String purpose, String refId, long expiry) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] out = mac.doFinal((purpose + "." + refId + "." + expiry).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(out);
    } catch (Exception e) {
      throw new IllegalStateException("HMAC unavailable", e);
    }
  }

  @ResponseStatus(HttpStatus.GONE)
  public static class InvalidLinkException extends RuntimeException {
    public InvalidLinkException(String message) { super(message); }
  }
}
