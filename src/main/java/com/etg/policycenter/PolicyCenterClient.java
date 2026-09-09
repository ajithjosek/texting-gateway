package com.etg.policycenter;

import java.util.List;

/**
 * PolicyCenter port. Stub (local/dev) is the default bean; the REST implementation
 * activates with {@code etg.policycenter.enabled=true}.
 */
public interface PolicyCenterClient {
  /** Policies renewing on or before the given date (scheduler filters the 30-day window). */
  List<RenewalCandidate> renewalsDueBefore(java.time.LocalDate date);
}
