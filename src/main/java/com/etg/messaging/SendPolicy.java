package com.etg.messaging;

import com.etg.consent.ConsentDeniedException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

/**
 * S1 send governance for marketing topics: quiet hours (21:00–08:00 recipient-local)
 * and frequency cap (4 per rolling 30 days). Transactional topics
 * (servicing/billing/claims) are never throttled here.
 */
@Service
public class SendPolicy {
  static final int QUIET_START_HOUR = 21;
  static final int QUIET_END_HOUR = 8;
  static final int MAX_MARKETING_PER_30_DAYS = 4;
  static final String DEFAULT_TIMEZONE = "America/New_York";

  private final MessageRepository messages;
  private final Clock clock;

  public SendPolicy(MessageRepository messages, Clock clock) {
    this.messages = messages;
    this.clock = clock;
  }

  public void check(String phoneE164, String topic, String recipientTimezone) {
    if (!"marketing".equals(topic)) return;
    ZoneId zone = parseZone(recipientTimezone);
    int hour = LocalTime.now(clock.withZone(zone)).getHour();
    if (hour >= QUIET_START_HOUR || hour < QUIET_END_HOUR) {
      throw new ConsentDeniedException(
          "CONSENT_DENIED_quiet_hours for " + phoneE164 + " in " + zone);
    }
    long sent = messages.countByToPhoneAndTopicAndCreatedAtAfter(
        phoneE164, "marketing", Instant.now(clock).minus(30, ChronoUnit.DAYS));
    if (sent >= MAX_MARKETING_PER_30_DAYS) {
      throw new ConsentDeniedException("CONSENT_DENIED_marketing_cap for " + phoneE164);
    }
  }

  private static ZoneId parseZone(String tz) {
    if (tz == null || tz.isBlank()) return ZoneId.of(DEFAULT_TIMEZONE);
    try {
      return ZoneId.of(tz);
    } catch (Exception e) {
      return ZoneId.of(DEFAULT_TIMEZONE);
    }
  }
}
