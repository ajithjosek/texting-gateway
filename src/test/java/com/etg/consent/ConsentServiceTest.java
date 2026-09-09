package com.etg.consent;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ConsentServiceTest {
  @Autowired ConsentService service;
  @Autowired ConsentRepository repo;

  @Test
  void optInThenRequirePasses_optOutThenDenied() {
    service.optIn("+15550001111", "servicing", "api", "v1", "test");
    assertThatCode(() -> service.requireOptIn("+15550001111", "servicing")).doesNotThrowAnyException();
    service.optOut("+15550001111", "servicing", "keyword", "twilio");
    assertThatThrownBy(() -> service.requireOptIn("+15550001111", "servicing"))
        .isInstanceOf(ConsentDeniedException.class);
  }
}
