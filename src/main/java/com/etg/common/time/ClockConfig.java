package com.etg.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Injected clock (overridable in tests via @MockBean) for quiet-hour and cap windows. */
@Configuration
public class ClockConfig {
  @Bean
  Clock clock() {
    return Clock.systemDefaultZone();
  }
}
