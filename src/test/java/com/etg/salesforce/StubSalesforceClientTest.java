package com.etg.salesforce;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StubSalesforceClientTest {

  @Test
  void upsertContact_isIdempotentPerPhone() {
    StubSalesforceClient sf = new StubSalesforceClient();
    SfContact a = sf.upsertContact("+15550011001", "servicing");
    SfContact b = sf.upsertContact("+15550011001", "billing");
    assertThat(a.id()).isEqualTo(b.id());
    assertThat(sf.findByPhone("+15550011001")).contains(a);
    assertThat(sf.findByPhone("+15550011002")).isEmpty();
  }

  @Test
  void logTask_createsContactImplicitly_andRecords() {
    StubSalesforceClient sf = new StubSalesforceClient();
    sf.logTask("+15550011003", "ETG SMS sent [billing]", "sid=SM1");
    assertThat(sf.tasks()).hasSize(1);
    assertThat(sf.tasks().get(0).subject()).contains("billing");
    assertThat(sf.findByPhone("+15550011003")).isPresent();
  }
}
