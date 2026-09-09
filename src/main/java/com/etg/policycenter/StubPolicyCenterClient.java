package com.etg.policycenter;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.beans.factory.annotation.Value;

/** In-memory renewals until PolicyCenter credentials are configured. */
public class StubPolicyCenterClient implements PolicyCenterClient {
  private final Clock clock;
  private final List<RenewalCandidate> candidates = new CopyOnWriteArrayList<>();

  public StubPolicyCenterClient(Clock clock,
                                @Value("${etg.demo-data:false}") boolean demoData) {
    this.clock = clock;
    if (demoData) {
      candidates.add(new RenewalCandidate("POL-DEMO-1", "+15550019999", "Demo",
          LocalDate.now(clock).plusDays(20), "P&C"));
    }
  }

  public void addCandidate(RenewalCandidate c) {
    candidates.add(c);
  }

  @Override
  public List<RenewalCandidate> renewalsDueBefore(LocalDate date) {
    return candidates.stream().filter(c -> !c.dueDate().isAfter(date)).toList();
  }
}
