package com.etg.policycenter;

import com.etg.scheduler.RenewalScheduler;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/hub/policycenter")
public class PolicyCenterController {
  private final PolicyCenterClient policies;
  private final RenewalScheduler renewals;
  private final boolean enabled;

  public PolicyCenterController(PolicyCenterClient policies, RenewalScheduler renewals,
                                @Value("${etg.policycenter.enabled:false}") boolean enabled) {
    this.policies = policies;
    this.renewals = renewals;
    this.enabled = enabled;
  }

  @GetMapping("/status")
  public Map<String, String> status() {
    return Map.of("enabled", String.valueOf(enabled),
        "mode", policies instanceof RestPolicyCenterClient ? "rest" : "stub");
  }

  @PostMapping("/run")
  public Map<String, Integer> run() {
    RenewalScheduler.RunResult r = renewals.runOnce();
    return Map.of("sent", r.sent(), "skipped", r.skipped());
  }
}
