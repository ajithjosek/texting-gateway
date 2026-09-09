package com.etg.messaging;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** TwilioSender without credentials must stub (local dev) rather than call the network. */
class TwilioSenderTest {

  @Test
  void send_withoutCredentials_returnsStubSid() {
    TwilioSender sender = new TwilioSender(); // @Value fields default to ""
    String sid = sender.send("+15550009111", "hello");
    assertThat(sid).isEqualTo("SM-stub-no-credentials");
  }
}
