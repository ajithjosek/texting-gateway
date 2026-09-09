package com.etg.claimcenter;

import static org.assertj.core.api.Assertions.*;

import com.etg.claimcenter.FnolRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class StubClaimCenterClientTest {

  private StubClaimCenterClient client() {
    return new StubClaimCenterClient(Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneId.of("UTC")));
  }

  @Test
  void statusOf_seededClaims_caseInsensitive() {
    assertThat(client().statusOf("clm-1001")).contains(
        new ClaimStatus("CLM-1001", "Open - inspection scheduled", "R. Diaz", "Jun 10"));
    assertThat(client().statusOf("CLM-9999")).isEmpty();
  }

  @Test
  void slotsFor_threeLabeledSlots_unknownClaimEmpty() {
    var slots = client().slotsFor("CLM-1001");
    assertThat(slots).hasSize(3);
    assertThat(slots).extracting(AdjusterSlot::id).containsExactly("S1", "S2", "S3");
    assertThat(slots).extracting(AdjusterSlot::label).doesNotHaveDuplicates();
    assertThat(slots.get(0).label()).contains("Jun");
    assertThat(client().slotsFor("CLM-9999")).isEmpty();
  }

  @Test
  void bookSlot_confirmsKnown_rejectsUnknown() {
    assertThat(client().bookSlot("CLM-1001", "S2", "+15550012001"))
        .map(Booking::confirmation)
        .hasValueSatisfying(c -> assertThat(c).startsWith("BKG-"));
    assertThat(client().bookSlot("CLM-1001", "S9", "+15550012001")).isEmpty();
  }

  @Test
  void createFnol_mintsNumber_visibleToStatus() {
    var stub = client();
    var filed = stub.createFnol(
        new FnolRequest("+15550012003", "POL-5", "2026-05-30", "AUTO"));
    assertThat(filed).map(FnolResult::claimNumber)
        .hasValueSatisfying(n -> assertThat(n).startsWith("CLM-"));
    assertThat(stub.statusOf(filed.get().claimNumber())).isPresent();
  }
}
