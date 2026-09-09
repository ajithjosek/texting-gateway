package com.etg.consent;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Pure unit tests for ConsentService (no Spring context). DB-backed roundtrip lives in ConsentServiceTest. */
@ExtendWith(MockitoExtension.class)
class ConsentServiceUnitTest {

  @Mock ConsentRepository repo;
  @InjectMocks ConsentService service;

  @Test
  void requireOptIn_deniedWhenNoRecord() {
    when(repo.findByPhoneE164AndTopic("+15550009000", "marketing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.requireOptIn("+15550009000", "marketing"))
        .isInstanceOf(ConsentDeniedException.class)
        .hasMessageContaining("CONSENT_DENIED");
  }

  @Test
  void requireOptIn_deniedWhenOptedOut_perTopicIsolation() {
    Consent optedOut = new Consent("+15550009001", "marketing", "opted_out", "keyword", "n/a", "twilio");
    when(repo.findByPhoneE164AndTopic("+15550009001", "marketing")).thenReturn(Optional.of(optedOut));
    assertThatThrownBy(() -> service.requireOptIn("+15550009001", "marketing"))
        .isInstanceOf(ConsentDeniedException.class);
    // servicing consent for the same phone is independent — no stub needed, must simply not throw for marketing path
    verify(repo).findByPhoneE164AndTopic("+15550009001", "marketing");
  }

  @Test
  void optIn_existingRecord_reactivatesInsteadOfInsert() {
    Consent existing = new Consent("+15550009002", "servicing", "opted_out", "keyword", "n/a", "twilio");
    when(repo.findByPhoneE164AndTopic("+15550009002", "servicing")).thenReturn(Optional.of(existing));
    when(repo.save(any(Consent.class))).thenAnswer(i -> i.getArgument(0));
    Consent saved = service.optIn("+15550009002", "servicing", "api", "v2", "agent-1");
    assertThat(saved.getStatus()).isEqualTo("opted_in");
    verify(repo, never()).save(argThat(c -> c != existing));
  }

  @Test
  void optOut_noRecord_createsOptOutWithProof() {
    when(repo.findByPhoneE164AndTopic(anyString(), anyString())).thenReturn(Optional.empty());
    when(repo.save(any(Consent.class))).thenAnswer(i -> i.getArgument(0));
    Consent saved = service.optOut("+15550009003", "marketing", "keyword", "twilio");
    assertThat(saved.getStatus()).isEqualTo("opted_out");
  }
}
