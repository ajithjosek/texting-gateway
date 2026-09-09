package com.etg.salesforce;

import java.util.Optional;

/**
 * Salesforce port. Stub (local/dev) is the default bean; the REST implementation
 * activates with {@code etg.salesforce.enabled=true}. All callers must treat this
 * as best-effort — SalesforceSync wraps every call fail-open.
 */
public interface SalesforceClient {
  Optional<SfContact> findByPhone(String phoneE164);
  SfContact upsertContact(String phoneE164, String topic);
  void logTask(String phoneE164, String subject, String description);
}
