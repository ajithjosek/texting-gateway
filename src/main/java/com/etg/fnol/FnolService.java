package com.etg.fnol;

import com.etg.claimcenter.ClaimCenterClient;
import com.etg.claimcenter.FnolRequest;
import com.etg.inbox.InboxService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Multi-step FNOL intake over stateless SMS. CLAIM starts/restarts, CANCEL aborts,
 * anything else advances the caller's session. Sessions idle over 30 minutes expire.
 * Completion files the FNOL and leaves an agent-visible thread note.
 */
@Service
public class FnolService {
  static final Duration SESSION_TTL = Duration.ofMinutes(30);
  private static final Set<String> LOSS_TYPES = Set.of("AUTO", "HOME", "OTHER");

  private final FnolSessionRepository sessions;
  private final ClaimCenterClient claims;
  private final InboxService inbox;
  private final Clock clock;

  public FnolService(FnolSessionRepository sessions, ClaimCenterClient claims,
                     InboxService inbox, Clock clock) {
    this.sessions = sessions;
    this.claims = claims;
    this.inbox = inbox;
    this.clock = clock;
  }

  public boolean hasActiveSession(String phoneE164) {
    return liveSession(phoneE164).isPresent();
  }

  @Transactional
  public String start(String phoneE164) {
    sessions.findByPhoneE164(phoneE164).ifPresent(sessions::delete);
    FnolSession s = new FnolSession(phoneE164);
    s.touch(now());
    sessions.save(s);
    return "Let's file your claim. Reply with your policy number.";
  }

  @Transactional
  public String cancel(String phoneE164) {
    sessions.findByPhoneE164(phoneE164).ifPresent(sessions::delete);
    return "Claim filing cancelled. Reply CLAIM any time to start over.";
  }

  @Transactional
  public String advance(String phoneE164, String body) {
    Optional<FnolSession> maybe = liveSession(phoneE164);
    if (maybe.isEmpty()) return null; // no session: caller falls through to keyword routing
    FnolSession s = maybe.get();
    String input = body == null ? "" : body.trim();
    return switch (s.getStep()) {
      case FnolSession.VERIFY_POLICY -> {
        if (input.isEmpty()) yield "Reply with your policy number.";
        s.setPolicyNumber(input.toUpperCase());
        s.setStep(FnolSession.LOSS_DATE);
        s.touch(now());
        sessions.save(s);
        yield "Thanks. Reply with the loss date (YYYY-MM-DD).";
      }
      case FnolSession.LOSS_DATE -> {
        if (!isDate(input)) yield "That date didn't parse. Reply with the loss date (YYYY-MM-DD).";
        s.setLossDate(input);
        s.setStep(FnolSession.LOSS_TYPE);
        s.touch(now());
        sessions.save(s);
        yield "Got it. Reply with the loss type: AUTO, HOME, or OTHER.";
      }
      default -> {
        String type = input.toUpperCase();
        if (!LOSS_TYPES.contains(type)) {
          yield "Please reply AUTO, HOME, or OTHER.";
        }
        var filed = claims.createFnol(
            new FnolRequest(phoneE164, s.getPolicyNumber(), s.getLossDate(), type));
        sessions.delete(s);
        if (filed.isEmpty()) {
          inbox.noteInbound(phoneE164, "claims",
              "FNOL failed for policy " + s.getPolicyNumber() + ", needs agent.");
          yield "We couldn't file that automatically. An agent will call you shortly to finish.";
        }
        inbox.noteInbound(phoneE164, "claims",
            "FNOL filed " + filed.get().claimNumber() + " for policy " + s.getPolicyNumber() + ".");
        yield "Filed! Your claim number is " + filed.get().claimNumber()
            + ". Reply STATUS " + filed.get().claimNumber() + " any time for updates.";
      }
    };
  }

  private Optional<FnolSession> liveSession(String phoneE164) {
    Optional<FnolSession> maybe = sessions.findByPhoneE164(phoneE164);
    if (maybe.isPresent() && maybe.get().getUpdatedAt().isBefore(now().minus(SESSION_TTL))) {
      sessions.delete(maybe.get());
      return Optional.empty();
    }
    return maybe;
  }

  private static boolean isDate(String input) {
    try {
      LocalDate.parse(input);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private Instant now() { return Instant.now(clock); }
}
