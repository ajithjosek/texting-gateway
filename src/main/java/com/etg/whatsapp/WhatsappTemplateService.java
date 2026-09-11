package com.etg.whatsapp;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Local Content API registry + approval sync. Only approved rows may send. */
@Service
public class WhatsappTemplateService {
  private final ContentTemplateRepository repo;
  private final TwilioContentClient content;
  private final Clock clock;

  public WhatsappTemplateService(ContentTemplateRepository repo, TwilioContentClient content, Clock clock) {
    this.repo = repo;
    this.content = content;
    this.clock = clock;
  }

  @Transactional
  public ContentTemplate register(String key, String channel, String contentSid) {
    ContentTemplate t = new ContentTemplate(key, channel == null ? ContentTemplate.WHATSAPP : channel, contentSid);
    t.touch(now());
    return repo.save(t);
  }

  @Transactional
  public int syncStatuses() {
    List<ContentTemplate> active = repo.findByActiveTrueOrderByIdAsc();
    int updated = 0;
    for (ContentTemplate t : active) {
      Optional<String> status = ContentTemplate.WHATSAPP.equals(t.getChannel())
          ? content.whatsappApproval(t.getContentSid())
          : Optional.empty();
      if (status.isPresent() && !status.get().equals(t.getStatus())) {
        t.setStatus(status.get());
        t.touch(now());
        repo.save(t);
        updated++;
      }
    }
    return updated;
  }

  public Optional<ContentTemplate> approvedFor(String contentSid) {
    if (contentSid == null) return Optional.empty();
    return repo.findFirstByContentSidAndActiveTrue(contentSid)
        .filter(t -> ContentTemplate.APPROVED.equals(t.getStatus()));
  }

  private Instant now() { return Instant.now(clock); }
}
