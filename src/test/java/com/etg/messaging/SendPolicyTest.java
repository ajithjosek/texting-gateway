package com.etg.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etg.consent.ConsentDeniedException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Quiet-hour windows and marketing cap, verified against fixed clocks. */
@ExtendWith(MockitoExtension.class)
class SendPolicyTest {

  @Mock MessageRepository messages;

  private SendPolicy policyAt(String utcInstant) {
    Clock clock = Clock.fixed(Instant.parse(utcInstant), ZoneId.of("UTC"));
    return new SendPolicy(messages, clock);
  }

  @Test
  void marketing_blockedAtNight_recipientLocal() {
    // 2026-06-01T02:00Z == 22:00 America/New_York (EDT)
    SendPolicy p = policyAt("2026-06-01T02:00:00Z");
    assertThatThrownBy(() -> p.check("+15550006001", "marketing", "America/New_York"))
        .isInstanceOf(ConsentDeniedException.class)
        .hasMessageContaining("quiet_hours");
    verifyNoInteractions(messages);
  }

  @Test
  void marketing_allowedDuringDay() {
    // 2026-06-01T14:00Z == 10:00 America/New_York
    SendPolicy p = policyAt("2026-06-01T14:00:00Z");
    when(messages.countByToPhoneAndTopicAndCreatedAtAfter(anyString(), eq("marketing"), any()))
        .thenReturn(0L);
    assertThatCode(() -> p.check("+15550006002", "marketing", "America/New_York"))
        .doesNotThrowAnyException();
  }

  @Test
  void boundaries_8amAllowed_9pmBlocked() {
    SendPolicy morning = policyAt("2026-06-01T12:00:00Z"); // 08:00 EDT
    when(messages.countByToPhoneAndTopicAndCreatedAtAfter(anyString(), eq("marketing"), any()))
        .thenReturn(0L);
    assertThatCode(() -> morning.check("+15550006003", "marketing", "America/New_York"))
        .doesNotThrowAnyException();

    SendPolicy evening = policyAt("2026-06-02T01:00:00Z"); // 21:00 EDT
    assertThatThrownBy(() -> evening.check("+15550006003", "marketing", "America/New_York"))
        .hasMessageContaining("quiet_hours");
  }

  @Test
  void transactionalTopics_neverThrottled_evenAtNight() {
    SendPolicy p = policyAt("2026-06-01T03:00:00Z"); // 23:00 EDT
    for (String topic : new String[] {"servicing", "billing", "claims"}) {
      assertThatCode(() -> p.check("+15550006004", topic, "America/New_York"))
          .doesNotThrowAnyException();
    }
    verifyNoInteractions(messages);
  }

  @Test
  void marketing_cap_fourthAllowed_fifthDenied() {
    SendPolicy p = policyAt("2026-06-01T14:00:00Z");
    when(messages.countByToPhoneAndTopicAndCreatedAtAfter(anyString(), eq("marketing"), any()))
        .thenReturn(3L, 4L);
    assertThatCode(() -> p.check("+15550006005", "marketing", "America/New_York"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> p.check("+15550006005", "marketing", "America/New_York"))
        .isInstanceOf(ConsentDeniedException.class)
        .hasMessageContaining("marketing_cap");
  }

  @Test
  void invalidOrMissingTimezone_fallsBackToDefault() {
    SendPolicy p = policyAt("2026-06-01T14:00:00Z"); // 10:00 ET default
    when(messages.countByToPhoneAndTopicAndCreatedAtAfter(anyString(), eq("marketing"), any()))
        .thenReturn(0L);
    assertThatCode(() -> p.check("+15550006006", "marketing", "Not/AZone"))
        .doesNotThrowAnyException();
    assertThatCode(() -> p.check("+15550006006", "marketing", null))
        .doesNotThrowAnyException();
  }
}
