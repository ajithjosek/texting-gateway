package com.etg.salesforce;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/hub/salesforce")
public class SalesforceController {
  private final SalesforceClient client;
  private final boolean enabled;

  public SalesforceController(SalesforceClient client,
                              @Value("${etg.salesforce.enabled:false}") boolean enabled) {
    this.client = client;
    this.enabled = enabled;
  }

  @GetMapping("/status")
  public Map<String, String> status() {
    return Map.of("enabled", String.valueOf(enabled),
        "mode", client instanceof RestSalesforceClient ? "rest" : "stub");
  }
}
