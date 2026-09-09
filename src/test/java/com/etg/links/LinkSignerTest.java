package com.etg.links;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class LinkSignerTest {

  private LinkSigner signerAt(String utcInstant) {
    return new LinkSigner(Clock.fixed(Instant.parse(utcInstant), ZoneId.of("UTC")), "test-secret");
  }

  @Test
  void roundtrip_returnsRefId() {
    LinkSigner s = signerAt("2026-06-01T12:00:00Z");
    String token = s.sign("pay", "POL-123", Duration.ofDays(7));
    assertThat(s.verify("pay", token)).isEqualTo("POL-123");
  }

  @Test
  void wrongPurpose_rejected() {
    LinkSigner s = signerAt("2026-06-01T12:00:00Z");
    String token = s.sign("pay", "POL-123", Duration.ofDays(7));
    assertThatThrownBy(() -> s.verify("esign", token))
        .isInstanceOf(LinkSigner.InvalidLinkException.class).hasMessageContaining("signature");
  }

  @Test
  void tamperedToken_rejected() {
    LinkSigner s = signerAt("2026-06-01T12:00:00Z");
    String token = s.sign("pay", "POL-123", Duration.ofDays(7));
    assertThatThrownBy(() -> s.verify("pay", token.replaceFirst("\\.", ".X")))
        .isInstanceOf(LinkSigner.InvalidLinkException.class);
  }

  @Test
  void expiredToken_rejected() {
    LinkSigner signer = signerAt("2026-06-01T12:00:00Z");
    String token = signer.sign("pay", "POL-123", Duration.ofHours(1));
    LinkSigner later = signerAt("2026-06-01T14:00:00Z");
    assertThatThrownBy(() -> later.verify("pay", token))
        .isInstanceOf(LinkSigner.InvalidLinkException.class).hasMessageContaining("EXPIRED");
  }

  @Test
  void malformedToken_rejected() {
    LinkSigner s = signerAt("2026-06-01T12:00:00Z");
    assertThatThrownBy(() -> s.verify("pay", "garbage"))
        .isInstanceOf(LinkSigner.InvalidLinkException.class).hasMessageContaining("MALFORMED");
  }
}
