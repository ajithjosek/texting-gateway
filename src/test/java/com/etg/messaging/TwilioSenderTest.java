package com.etg.messaging;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** TwilioSender without credentials must stub (local dev) rather than call the network. */
class TwilioSenderTest {

  @Test
  void send_withoutCredentials_returnsUniqueStubSids() {
    TwilioSender sender = new TwilioSender(); // @Value fields default to ""
    String a = sender.send("+15550009111", "hello");
    String b = sender.send("+15550009111", "hello");
    assertThat(a).startsWith("SM-stub-");
    assertThat(b).startsWith("SM-stub-").isNotEqualTo(a);
  }
}
