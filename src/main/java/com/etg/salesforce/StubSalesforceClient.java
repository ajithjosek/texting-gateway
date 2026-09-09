package com.etg.salesforce;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory stand-in: default bean until real Salesforce credentials are configured. */
public class StubSalesforceClient implements SalesforceClient {
  private final Map<String, SfContact> contacts = new ConcurrentHashMap<>();
  private final List<Task> tasks = new CopyOnWriteArrayList<>();

  public record Task(String contactId, String subject, String description) {}

  @Override
  public Optional<SfContact> findByPhone(String phoneE164) {
    return Optional.ofNullable(contacts.get(phoneE164));
  }

  @Override
  public SfContact upsertContact(String phoneE164, String topic) {
    return contacts.computeIfAbsent(phoneE164,
        p -> new SfContact("stub-" + UUID.randomUUID(), p));
  }

  @Override
  public void logTask(String phoneE164, String subject, String description) {
    String contactId = upsertContact(phoneE164, "sync").id();
    tasks.add(new Task(contactId, subject, description));
  }

  // Test inspection.
  public List<Task> tasks() { return List.copyOf(tasks); }
  public Map<String, SfContact> contacts() { return Map.copyOf(contacts); }
}
