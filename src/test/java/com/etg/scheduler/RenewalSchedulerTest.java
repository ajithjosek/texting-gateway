package com.etg.scheduler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etg.consent.ConsentDeniedException;
import com.etg.consent.ConsentService;
import com.etg.links.LinkSigner;
import com.etg.messaging.InvalidPhoneException;
import com.etg.messaging.MessageService;
import com.etg.messaging.PhoneValidator;
import com.etg.policycenter.PolicyCenterClient;
import com.etg.policycenter.RenewalCandidate;
import com.etg.template.TemplateService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RenewalSchedulerTest {

  @Mock PolicyCenterClient policies;
  @Mock TemplateService templates;
  @Mock MessageService messages;
  @Mock ConsentService consent;
  @Mock PhoneValidator phones;
  @Mock LinkSigner links;

  Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneId.of("UTC"));

  private RenewalScheduler scheduler() {
    return new RenewalScheduler(policies, templates, messages, consent, phones, links,
        clock, "http://base", true);
  }

  private RenewalCandidate cand(String policy, String phone, LocalDate due) {
    return new RenewalCandidate(policy, phone, "Ana", due, "P&C");
  }

  @Test
  void inWindow_sendsWithCyclePinnedKey_andEsignVars() {
    when(policies.renewalsDueBefore(LocalDate.parse("2026-07-01")))
        .thenReturn(List.of(cand("POL-1", "+15550014001", LocalDate.parse("2026-06-21"))));
    when(templates.render(eq("renewal_reminder"), eq("en"), anyMap()))
        .thenReturn("renew now");
    when(links.sign(eq("esign"), eq("POL-1"), any())).thenReturn("tok");

    RenewalScheduler.RunResult r = scheduler().runOnce();

    assertThat(r.sent()).isEqualTo(1);
    assertThat(r.skipped()).isEqualTo(0);
    verify(messages).send("+15550014001", "servicing", "renew now",
        "renewal:POL-1:2026-06-21", null);
    verify(templates).render(eq("renewal_reminder"), eq("en"),
        argThat(vars -> "Ana".equals(vars.get("first_name"))
            && ((String) vars.get("esign_link")).contains("tok")));
  }

  @Test
  void outOfWindow_ignoredWithoutDecision() {
    when(policies.renewalsDueBefore(LocalDate.parse("2026-07-01"))).thenReturn(List.of(
        cand("POL-past", "+15550014002", LocalDate.parse("2026-05-20")),
        cand("POL-far", "+15550014003", LocalDate.parse("2026-08-15"))));
    RenewalScheduler.RunResult r = scheduler().runOnce();
    assertThat(r.sent()).isEqualTo(0);
    assertThat(r.skipped()).isEqualTo(0);
    verifyNoInteractions(consent, messages);
  }

  @Test
  void nonConsented_skipped_notFailed() {
    when(policies.renewalsDueBefore(any())).thenReturn(
        List.of(cand("POL-2", "+15550014004", LocalDate.parse("2026-06-10"))));
    doThrow(new ConsentDeniedException("nope")).when(consent)
        .requireOptIn("+15550014004", "servicing");
    RenewalScheduler.RunResult r = scheduler().runOnce();
    assertThat(r.sent()).isEqualTo(0);
    assertThat(r.skipped()).isEqualTo(1);
    verifyNoInteractions(messages);
  }

  @Test
  void invalidPhone_skipped() {
    when(policies.renewalsDueBefore(any())).thenReturn(
        List.of(cand("POL-3", "bad", LocalDate.parse("2026-06-10"))));
    doThrow(new InvalidPhoneException("bad")).when(phones).validate("bad");
    assertThat(scheduler().runOnce().skipped()).isEqualTo(1);
    verifyNoInteractions(messages);
  }
}
