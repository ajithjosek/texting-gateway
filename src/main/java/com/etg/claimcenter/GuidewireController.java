package com.etg.claimcenter;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/hub/guidewire")
public class GuidewireController {
  private final ClaimCenterClient claims;
  private final boolean enabled;

  public GuidewireController(ClaimCenterClient claims,
                             @Value("${etg.guidewire.enabled:false}") boolean enabled) {
    this.claims = claims;
    this.enabled = enabled;
  }

  @GetMapping("/status")
  public Map<String, String> status() {
    return Map.of("enabled", String.valueOf(enabled),
        "mode", claims instanceof RestClaimCenterClient ? "rest" : "stub");
  }
}
