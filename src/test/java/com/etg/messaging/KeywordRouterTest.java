package com.etg.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.etg.claimcenter.ClaimCenterClient;
import com.etg.claimcenter.ClaimStatus;
import com.etg.consent.ConsentService;
import com.etg.inbox.InboxService;
import com.etg.salesforce.SalesforceSync;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KeywordRouterTest {

  @Mock ConsentService consent;
  @Mock BalanceProvider balances;
  @Mock InboxService inbox;
  @Mock SalesforceSync sync;
  @Mock ClaimCenterClient claims;
  @InjectMocks KeywordRouter router;

  @Test
  void stopVariants_optOutBothTopics_andConfirm() {
    for (String word : new String[] {"STOP", "stop all please", "Unsubscribe", "QUIT", "END", "CANCEL"}) {
      assertThat(router.route("+15550007001", word)).contains("unsubscribed");
    }
    verify(consent, times(6)).optOut("+15550007001", "marketing", "keyword", "twilio");
    verify(consent, times(6)).optOut("+15550007001", "servicing", "keyword", "twilio");
  }

  @Test
  void help_returnsHelp_withoutTouchingConsent() {
    assertThat(router.route("+15550007002", "help")).contains("Reply STOP");
    verifyNoInteractions(consent, balances);
  }

  @Test
  void bal_withLinkedAccount_returnsBalance() {
    when(balances.balanceFor("+15550007003")).thenReturn("Balance $120.00 due Dec 1.");
    assertThat(router.route("+15550007003", "BAL")).contains("$120.00");
  }

  @Test
  void bal_withoutLinkedAccount_isHonest() {
    when(balances.balanceFor("+15550007004")).thenReturn(null);
    assertThat(router.route("+15550007004", "bal")).contains("not connected yet");
  }

  @Test
  void status_found_returnsDetails_withoutInboxThread() {
    when(claims.statusOf("CLM-1001")).thenReturn(Optional.of(
        new ClaimStatus("CLM-1001", "Open", "R. Diaz", "Jun 10")));
    assertThat(router.route("+15550007005", "STATUS CLM-1001")).contains("R. Diaz");
    verifyNoInteractions(inbox);
  }

  @Test
  void status_missingNumber_returnsUsage() {
    assertThat(router.route("+15550007005", "STATUS")).contains("STATUS CLM-1001");
  }

  @Test
  void status_unknown_opensInboxThread() {
    when(claims.statusOf("CLM-9")).thenReturn(Optional.empty());
    assertThat(router.route("+15550007005", "STATUS CLM-9")).contains("couldn't find");
    verify(inbox).noteInbound("+15550007005", "claims", "STATUS CLM-9");
  }

  @Test
  void schedule_listsSlots_and_book_confirms() {
    when(claims.slotsFor("CLM-1001")).thenReturn(List.of(
        new com.etg.claimcenter.AdjusterSlot("S1", "CLM-1001", "Mon 9:00 AM ET")));
    assertThat(router.route("+15550007005", "SCHEDULE CLM-1001")).contains("S1");
    when(claims.bookSlot("CLM-1001", "S1", "+15550007005")).thenReturn(Optional.of(
        new com.etg.claimcenter.Booking("CLM-1001", "S1", "BKG-1")));
    assertThat(router.route("+15550007005", "BOOK CLM-1001 S1")).contains("BKG-1");
  }

  @Test
  void book_unknownSlot_opensInboxThread() {
    when(claims.bookSlot("CLM-1001", "S9", "+15550007005")).thenReturn(Optional.empty());
    assertThat(router.route("+15550007005", "BOOK CLM-1001 S9")).contains("no longer available");
  }

  @Test
  void futureKeywords_haveStableSeams() {
    assertThat(router.route("+15550007005", "CLAIM")).contains("S4");
    assertThat(router.route("+15550007005", "PAY")).contains("S3");
    verifyNoInteractions(consent);
  }

  @Test
  void unknownBody_routesToAgentInbox() {
    assertThat(router.route("+15550007006", "what is my deductible?")).contains("agent will reply");
    verifyNoInteractions(consent, balances);
  }

  @Test
  void nullBody_routesToAgentInbox() {
    assertThat(router.route("+15550007007", null)).contains("agent will reply");
  }
}
