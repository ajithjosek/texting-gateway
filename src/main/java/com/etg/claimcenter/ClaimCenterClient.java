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
  /** File a first notice of loss; empty when ClaimCenter rejects the request. */
  Optional<FnolResult> createFnol(FnolRequest request);
  /** Attach a stored photo to a claim; false when ClaimCenter rejects it. */
  boolean attachPhoto(String claimNumber, String mediaRef, String contentType);
}
