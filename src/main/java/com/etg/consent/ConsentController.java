package com.etg.consent;

import com.etg.messaging.PhoneValidator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/consent")
public class ConsentController {
  private final ConsentService service;
  private final PhoneValidator phones;

  public ConsentController(ConsentService service, PhoneValidator phones) {
    this.service = service; this.phones = phones;
  }

  public record ConsentRequest(String phoneE164, String topic, String source, String proofTextVersion, String actor) {}

  @PostMapping("/opt-in")
  @ResponseStatus(HttpStatus.OK)
  public Consent optIn(@RequestBody ConsentRequest r) {
    phones.validate(r.phoneE164());
    return service.optIn(r.phoneE164(), r.topic(), r.source(), r.proofTextVersion(), r.actor());
  }

  @PostMapping("/opt-out")
  @ResponseStatus(HttpStatus.OK)
  public Consent optOut(@RequestBody ConsentRequest r) {
    phones.validate(r.phoneE164());
    return service.optOut(r.phoneE164(), r.topic(), r.source(), r.actor());
  }
}
