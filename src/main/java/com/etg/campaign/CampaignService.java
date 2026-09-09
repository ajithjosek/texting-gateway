package com.etg.campaign;

import com.etg.messaging.MessageService;
import com.etg.template.TemplateService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Bulk campaigns with a legal approval gate. Launch renders the template once and
 * sends per recipient through the governed pipeline — consent, quiet hours and
 * frequency caps apply per number, so unconsented recipients are skipped, not sent.
 * Launches are synchronous and rate-limited (v1); async bulk lands with CAT scale work.
 */
@Service
public class CampaignService {
  private static final Logger log = LoggerFactory.getLogger(CampaignService.class);

  private final CampaignRepository campaigns;
  private final CampaignRecipientRepository recipients;
  private final TemplateService templates;
  private final MessageService messages;
  private final ObjectMapper json;
  private final Clock clock;
  private final int ratePerSecond;

  public CampaignService(CampaignRepository campaigns, CampaignRecipientRepository recipients,
                         TemplateService templates, MessageService messages,
                         ObjectMapper json, Clock clock,
                         @Value("${etg.campaign.rate-per-second:100}") int ratePerSecond) {
    this.campaigns = campaigns;
    this.recipients = recipients;
    this.templates = templates;
    this.messages = messages;
    this.json = json;
    this.clock = clock;
    this.ratePerSecond = Math.max(1, ratePerSecond);
  }

  @Transactional
  public Campaign create(String name, String templateKey, String locale,
                         Map<String, Object> vars, List<String> phones, String createdBy) {
    Campaign c;
    try {
      c = new Campaign(name, templateKey, locale, json.writeValueAsString(vars == null ? Map.of() : vars), createdBy);
    } catch (Exception e) {
      throw new IllegalArgumentException("CAMPAIGN_BAD_VARS", e);
    }
    c.touch(now());
    Campaign saved = campaigns.save(c);
    if (phones != null) {
      for (String p : phones) {
        recipients.save(new CampaignRecipient(saved.getId(), p));
      }
    }
    return saved;
  }

  @Transactional
  public Campaign submit(Long id) {
    Campaign c = get(id);
    require(c, Campaign.DRAFT, "submit");
    c.setStatus(Campaign.PENDING);
    c.touch(now());
    return campaigns.save(c);
  }

  @Transactional
  public Campaign approve(Long id, String approver) {
    Campaign c = get(id);
    require(c, Campaign.PENDING, "approve");
    if (approver != null && approver.equals(c.getCreatedBy())) {
      throw new SelfApprovalException("CAMPAIGN_SELF_APPROVAL: legal gate needs a second person");
    }
    c.setStatus(Campaign.APPROVED);
    c.setApprovedBy(approver);
    c.touch(now());
    return campaigns.save(c);
  }

  @Transactional
  public Campaign reject(Long id, String approver) {
    Campaign c = get(id);
    require(c, Campaign.PENDING, "reject");
    c.setStatus(Campaign.REJECTED);
    c.setApprovedBy(approver);
    c.touch(now());
    return campaigns.save(c);
  }

  public Campaign launch(Long id) {
    Campaign c = get(id);
    require(c, Campaign.APPROVED, "launch");
    c.setStatus(Campaign.RUNNING);
    c.touch(now());
    campaigns.save(c);

    Map<String, Object> vars = parseVars(c);
    String body = templates.render(c.getTemplateKey(), c.getLocale(), vars);
    long pauseMs = 1000L / ratePerSecond;
    int sent = 0, skipped = 0;
    for (CampaignRecipient r : recipients.findByCampaignIdOrderByIdAsc(id)) {
      try {
        messages.send(r.getPhoneE164(), c.getTopic(), body,
            "campaign:" + id + ":" + r.getPhoneE164(), null);
        r.setStatus(CampaignRecipient.SENT);
        sent++;
      } catch (RuntimeException e) {
        r.setStatus(CampaignRecipient.SKIPPED);
        r.setSkipReason(e.getMessage());
        skipped++;
        log.info("Campaign {} skipped {}: {}", id, r.getPhoneE164(), e.getMessage());
      }
      recipients.save(r);
      sleep(pauseMs);
    }
    c.setStatus(Campaign.DONE);
    c.setSentCount(sent);
    c.setSkippedCount(skipped);
    c.touch(now());
    return campaigns.save(c);
  }

  private Map<String, Object> parseVars(Campaign c) {
    try {
      return json.readValue(c.getVarsJson(), new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("CAMPAIGN_BAD_VARS", e);
    }
  }

  private Campaign get(Long id) {
    return campaigns.findById(id)
        .orElseThrow(() -> new NotFoundException("CAMPAIGN_NOT_FOUND: " + id));
  }

  private static void require(Campaign c, String status, String action) {
    if (!status.equals(c.getStatus())) {
      throw new StateException("CAMPAIGN_BAD_STATE: cannot " + action + " from " + c.getStatus());
    }
  }

  private static void sleep(long ms) {
    if (ms <= 0) return;
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private Instant now() { return Instant.now(clock); }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  public static class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
  }

  @ResponseStatus(HttpStatus.CONFLICT)
  public static class StateException extends RuntimeException {
    public StateException(String message) { super(message); }
  }

  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  public static class SelfApprovalException extends RuntimeException {
    public SelfApprovalException(String message) { super(message); }
  }
}
