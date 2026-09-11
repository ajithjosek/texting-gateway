package com.etg.consent;

import com.etg.outbox.OutboxEvent;
import com.etg.outbox.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** TCPA gate: every send must pass through here before Twilio is called. */
@Service
public class ConsentService {
  private final ConsentRepository repo;
  private final ConsentEventRepository events;
  private final OutboxRepository outbox;
  private final ObjectMapper json;

  public ConsentService(ConsentRepository repo, ConsentEventRepository events,
                        OutboxRepository outbox, ObjectMapper json) {
    this.repo = repo;
    this.events = events;
    this.outbox = outbox;
    this.json = json;
  }

  public void requireOptIn(String phoneE164, String topic) {
    Consent c = repo.findByPhoneE164AndTopic(phoneE164, topic).orElse(null);
    if (c == null || !"opted_in".equals(c.getStatus())) {
      throw new ConsentDeniedException("CONSENT_DENIED_no_opt_in for " + phoneE164 + "/" + topic);
    }
  }

  @Transactional
  public Consent optIn(String phone, String topic, String source, String proofVersion, String actor) {
    return transition(phone, topic, "opted_in", source, proofVersion, actor);
  }

  @Transactional
  public Consent optOut(String phone, String topic, String source, String actor) {
    return transition(phone, topic, "opted_out", source, "n/a", actor);
  }

  private Consent transition(String phone, String topic, String newStatus,
                             String source, String proofVersion, String actor) {
    Consent existing = repo.findByPhoneE164AndTopic(phone, topic).orElse(null);
    String oldStatus = existing == null ? null : existing.getStatus();
    Consent saved;
    if (existing == null) {
      saved = repo.save(new Consent(phone, topic, newStatus, source, proofVersion, actor));
    } else {
      existing.setStatus(newStatus);
      saved = repo.save(existing);
    }
    events.save(new ConsentEvent(phone, topic, oldStatus, newStatus, source, actor));
    try {
      outbox.save(new OutboxEvent("consent", phone + ":" + topic, "consent.updated",
          json.writeValueAsString(Map.of("phone", phone, "topic", topic,
              "oldStatus", oldStatus == null ? "" : oldStatus, "newStatus", newStatus,
              "source", source, "actor", actor == null ? "" : actor))));
    } catch (Exception e) {
      throw new IllegalStateException("Consent outbox serialization failed", e);
    }
    return saved;
  }
}
