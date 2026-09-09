package com.etg.claimcenter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Seeded demo claims until Guidewire credentials are configured. */
public class StubClaimCenterClient implements ClaimCenterClient {
  private static final ZoneId ET = ZoneId.of("America/New_York");
  private static final DateTimeFormatter FMT =
      DateTimeFormatter.ofPattern("EEE MMM d h:mm a z", Locale.US).withZone(ET);

  private final Clock clock;
  private final Map<String, ClaimStatus> claims = new ConcurrentHashMap<>();
  private final AtomicInteger fnolSeq = new AtomicInteger(2000);

  public StubClaimCenterClient(Clock clock) {
    this.clock = clock;
    claims.put("CLM-1001", new ClaimStatus("CLM-1001", "Open - inspection scheduled", "R. Diaz", "Jun 10"));
    claims.put("CLM-1002", new ClaimStatus("CLM-1002", "Closed - paid", "J. Park", "Completed"));
  }

  @Override
  public Optional<ClaimStatus> statusOf(String claimNumber) {
    return Optional.ofNullable(claims.get(claimNumber.toUpperCase()));
  }

  @Override
  public List<AdjusterSlot> slotsFor(String claimNumber) {
    String num = claimNumber.toUpperCase();
    if (!claims.containsKey(num)) return List.of();
    Instant base = Instant.now(clock);
    return List.of(
        slot(num, "S1", base.plusSeconds(24 * 3600)),
        slot(num, "S2", base.plusSeconds(48 * 3600)),
        slot(num, "S3", base.plusSeconds(72 * 3600)));
  }

  @Override
  public Optional<Booking> bookSlot(String claimNumber, String slotId, String phoneE164) {
    boolean known = slotsFor(claimNumber).stream()
        .anyMatch(s -> s.id().equalsIgnoreCase(slotId));
    if (!known) return Optional.empty();
    return Optional.of(new Booking(claimNumber.toUpperCase(), slotId.toUpperCase(),
        "BKG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
  }

  @Override
  public Optional<FnolResult> createFnol(FnolRequest request) {
    String number = "CLM-" + fnolSeq.incrementAndGet();
    claims.put(number, new ClaimStatus(number, "Open - intake review",
        "Unassigned", "Pending review"));
    return Optional.of(new FnolResult(number));
  }

  private AdjusterSlot slot(String claimNumber, String suffix, Instant at) {
    return new AdjusterSlot(suffix, claimNumber, FMT.format(at));
  }
}
