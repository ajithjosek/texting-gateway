package com.etg.salesforce;

import com.etg.inbox.Conversation;
import com.etg.inbox.ConversationMessage;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Best-effort CRM write-through. Every call is fail-open: Salesforce outages
 * never block sends or inbox work. Async migration (outbox-driven) can reuse
 * the message.created / delivery.updated event stream later.
 */
@Service
public class SalesforceSync {
  private static final Logger log = LoggerFactory.getLogger(SalesforceSync.class);
  private final SalesforceClient salesforce;

  public SalesforceSync(SalesforceClient salesforce) { this.salesforce = salesforce; }

  public void syncContact(String phoneE164, String topic) {
    attempt(() -> salesforce.upsertContact(phoneE164, topic));
  }

  public void logSend(String phoneE164, String topic, String sid) {
    attempt(() -> salesforce.logTask(phoneE164, "ETG SMS sent [" + topic + "]", "sid=" + sid));
  }

  public void pushTranscript(Conversation c, List<ConversationMessage> thread) {
    attempt(() -> {
      String body = thread.stream()
          .map(m -> ("IN".equals(m.getDirection()) ? "Customer: " : "Agent: ") + m.getBody())
          .collect(Collectors.joining("\n"));
      salesforce.logTask(c.getCustomerPhone(),
          "ETG transcript #" + c.getId() + " [" + c.getTopic() + "]",
          body.isBlank() ? "(empty)" : body);
    });
  }

  private static void attempt(Runnable r) {
    try {
      r.run();
    } catch (Exception e) {
      log.warn("Salesforce sync skipped: {}", e.getMessage());
    }
  }
}
