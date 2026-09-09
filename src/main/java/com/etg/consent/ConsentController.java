package com.etg.consent;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/consent")
public class ConsentController {
  private final ConsentService service;
  public ConsentController(ConsentService service) { this.service = service; }

  public record ConsentRequest(String phoneE164, String topic, String source, String proofTextVersion, String actor) {}

  @PostMapping("/opt-in")
  @ResponseStatus(HttpStatus.OK)
  public Consent optIn(@RequestBody ConsentRequest r) {
    return service.optIn(r.phoneE164(), r.topic(), r.source(), r.proofTextVersion(), r.actor());
  }

  @PostMapping("/opt-out")
  @ResponseStatus(HttpStatus.OK)
  public Consent optOut(@RequestBody ConsentRequest r) {
    return service.optOut(r.phoneE164(), r.topic(), r.source(), r.actor());
  }
}
