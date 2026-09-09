package com.etg.scheduler;

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
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily renewal reminders: policies renewing within 30 days get one templated
 * SMS each per cycle (idempotency key pins policy + due date). Non-consented or
 * invalid numbers are skipped, never failed. Disabled in tests via
 * {@code etg.renewal.enabled=false}; operators trigger manually via
 * {@code POST /v1/hub/policycenter/run}.
 */
@Component
public class RenewalScheduler {
  private static final Logger log = LoggerFactory.getLogger(RenewalScheduler.class);
  static final int WINDOW_DAYS = 30;
  private static final DateTimeFormatter DUE_FMT =
      DateTimeFormatter.ofPattern("MMM d", Locale.US);

  private final PolicyCenterClient policies;
  private final TemplateService templates;
  private final MessageService messages;
  private final ConsentService consent;
  private final PhoneValidator phones;
  private final LinkSigner links;
  private final Clock clock;
  private final String publicBaseUrl;
  private final boolean enabled;

  public RenewalScheduler(PolicyCenterClient policies, TemplateService templates,
                          MessageService messages, ConsentService consent,
                          PhoneValidator phones, LinkSigner links, Clock clock,
                          @Value("${etg.public-base-url:http://localhost:8080}") String publicBaseUrl,
                          @Value("${etg.renewal.enabled:true}") boolean enabled) {
    this.policies = policies;
    this.templates = templates;
    this.messages = messages;
    this.consent = consent;
    this.phones = phones;
    this.links = links;
    this.clock = clock;
    this.publicBaseUrl = publicBaseUrl;
    this.enabled = enabled;
  }

  @Scheduled(fixedDelayString = "${etg.renewal-delay-ms:86400000}", initialDelayString = "${etg.renewal-initial-ms:60000}")
  public void scheduled() {
    if (!enabled) return;
    RunResult r = runOnce();
    log.info("Renewal run: {} sent, {} skipped", r.sent(), r.skipped());
  }

  public RunResult runOnce() {
    LocalDate today = LocalDate.now(clock);
    LocalDate horizon = today.plusDays(WINDOW_DAYS);
    int sent = 0, skipped = 0;
    for (RenewalCandidate c : policies.renewalsDueBefore(horizon)) {
      if (c.dueDate().isBefore(today) || c.dueDate().isAfter(horizon)) {
        continue; // outside the reminder window, no decision
      }
      try {
        phones.validate(c.phoneE164());
        consent.requireOptIn(c.phoneE164(), "servicing");
      } catch (InvalidPhoneException | ConsentDeniedException e) {
        skipped++;
        continue;
      }
      Map<String, Object> vars = new HashMap<>();
      vars.put("first_name", c.firstName() == null ? "there" : c.firstName());
      vars.put("policy_number", c.policyNumber());
      vars.put("due_date", DUE_FMT.format(c.dueDate()));
      vars.put("esign_link", publicBaseUrl + "/esign?t="
          + links.sign("esign", c.policyNumber(), Duration.ofDays(30)));
      String body = templates.render("renewal_reminder", "en", vars);
      messages.send(c.phoneE164(), "servicing", body,
          "renewal:" + c.policyNumber() + ":" + c.dueDate(), null);
      sent++;
    }
    return new RunResult(sent, skipped);
  }

  public record RunResult(int sent, int skipped) {}
}
