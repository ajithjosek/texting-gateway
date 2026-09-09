package com.etg.claimcenter;

import java.util.List;
import java.util.Optional;

/**
 * ClaimCenter port. Stub (local/dev) is the default bean; the REST implementation
 * activates with {@code etg.guidewire.enabled=true}. FNOL creation lands next (S4).
 */
public interface ClaimCenterClient {
  Optional<ClaimStatus> statusOf(String claimNumber);
  List<AdjusterSlot> slotsFor(String claimNumber);
  Optional<Booking> bookSlot(String claimNumber, String slotId, String phoneE164);
}
