package com.etg.consent;

import org.springframework.stereotype.Service;

/** TCPA gate: every send must pass through here before Twilio is called. */
@Service
public class ConsentService {
  private final ConsentRepository repo;

  public ConsentService(ConsentRepository repo) { this.repo = repo; }

  public void requireOptIn(String phoneE164, String topic) {
    Consent c = repo.findByPhoneE164AndTopic(phoneE164, topic).orElse(null);
    if (c == null || !"opted_in".equals(c.getStatus())) {
      throw new ConsentDeniedException("CONSENT_DENIED_no_opt_in for " + phoneE164 + "/" + topic);
    }
  }

  public Consent optIn(String phone, String topic, String source, String proofVersion, String actor) {
    return repo.findByPhoneE164AndTopic(phone, topic)
        .map(existing -> { existing.setStatus("opted_in"); return repo.save(existing); })
        .orElseGet(() -> repo.save(new Consent(phone, topic, "opted_in", source, proofVersion, actor)));
  }

  public Consent optOut(String phone, String topic, String source, String actor) {
    return repo.findByPhoneE164AndTopic(phone, topic)
        .map(existing -> { existing.setStatus("opted_out"); return repo.save(existing); })
        .orElseGet(() -> repo.save(new Consent(phone, topic, "opted_out", source, "n/a", actor)));
  }
}
