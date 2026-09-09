package com.etg.fnol;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etg.claimcenter.ClaimCenterClient;
import com.etg.claimcenter.FnolRequest;
import com.etg.claimcenter.FnolResult;
import com.etg.inbox.InboxService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FnolServiceTest {

  @Mock FnolSessionRepository sessions;
  @Mock ClaimCenterClient claims;
  @Mock InboxService inbox;

  Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneId.of("UTC"));
  FnolService fnol;

  @BeforeEach
  void setUp() {
    fnol = new FnolService(sessions, claims, inbox, clock);
  }

  private FnolSession sessionAt(String step) {
    FnolSession s = new FnolSession("+15550013001");
    s.setStep(step);
    s.setPolicyNumber("POL-1");
    s.setLossDate("2026-05-30");
    s.touch(Instant.now(clock));
    return s;
  }

  @Test
  void start_createsSession_andPromptsPolicy() {
    when(sessions.findByPhoneE164("+15550013001")).thenReturn(Optional.empty());
    assertThat(fnol.start("+15550013001")).contains("policy number");
    verify(sessions).save(any(FnolSession.class));
  }

  @Test
  void advance_policyStep_movesToDate() {
    when(sessions.findByPhoneE164("+15550013001")).thenReturn(Optional.of(sessionAt(FnolSession.VERIFY_POLICY)));
    assertThat(fnol.advance("+15550013001", "pol-42")).contains("YYYY-MM-DD");
    verify(sessions).save(argThat(s ->
        "POL-42".equals(s.getPolicyNumber()) && FnolSession.LOSS_DATE.equals(s.getStep())));
  }

  @Test
  void advance_badDate_reprompts() {
    when(sessions.findByPhoneE164("+15550013001")).thenReturn(Optional.of(sessionAt(FnolSession.LOSS_DATE)));
    assertThat(fnol.advance("+15550013001", "yesterday")).contains("didn't parse");
    verify(sessions, never()).save(any());
    verifyNoInteractions(claims);
  }

  @Test
  void advance_badType_reprompts() {
    when(sessions.findByPhoneE164("+15550013001")).thenReturn(Optional.of(sessionAt(FnolSession.LOSS_TYPE)));
    assertThat(fnol.advance("+15550013001", "BOAT")).contains("AUTO, HOME, or OTHER");
    verifyNoInteractions(claims);
  }

  @Test
  void advance_validType_filesFnol_clearsSession_notesInbox() {
    when(sessions.findByPhoneE164("+15550013001")).thenReturn(Optional.of(sessionAt(FnolSession.LOSS_TYPE)));
    when(claims.createFnol(any(FnolRequest.class))).thenReturn(Optional.of(new FnolResult("CLM-2001")));

    assertThat(fnol.advance("+15550013001", "auto")).contains("CLM-2001");
    verify(claims).createFnol(argThat(r ->
        "POL-1".equals(r.policyNumber()) && "2026-05-30".equals(r.lossDate())
            && "AUTO".equals(r.lossType())));
    verify(sessions).delete(any(FnolSession.class));
    verify(inbox).noteInbound(eq("+15550013001"), eq("claims"), contains("CLM-2001"));
  }

  @Test
  void advance_claimCenterRejects_agentFallback() {
    when(sessions.findByPhoneE164("+15550013001")).thenReturn(Optional.of(sessionAt(FnolSession.LOSS_TYPE)));
    when(claims.createFnol(any(FnolRequest.class))).thenReturn(Optional.empty());

    assertThat(fnol.advance("+15550013001", "HOME")).contains("agent will call");
    verify(inbox).noteInbound(eq("+15550013001"), eq("claims"), contains("needs agent"));
  }

  @Test
  void expiredSession_isDropped_andAdvanceFallsThrough() {
    FnolSession stale = sessionAt(FnolSession.LOSS_DATE);
    stale.touch(Instant.now(clock).minusSeconds(3600));
    when(sessions.findByPhoneE164("+15550013001")).thenReturn(Optional.of(stale));

    assertThat(fnol.hasActiveSession("+15550013001")).isFalse();
    assertThat(fnol.advance("+15550013001", "2026-05-30")).isNull();
    verify(sessions, atLeastOnce()).delete(stale);
  }

  @Test
  void cancel_clearsSession() {
    when(sessions.findByPhoneE164("+15550013001")).thenReturn(Optional.of(sessionAt(FnolSession.LOSS_DATE)));
    assertThat(fnol.cancel("+15550013001")).contains("cancelled");
    verify(sessions).delete(any(FnolSession.class));
  }
}
