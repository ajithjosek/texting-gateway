package com.etg.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.etg.consent.ConsentService;
import com.etg.inbox.InboxService;
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
  void futureKeywords_haveStableSeams() {
    assertThat(router.route("+15550007005", "STATUS 12345")).contains("S4");
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
