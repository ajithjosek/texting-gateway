package com.etg.messaging;

import com.etg.consent.ConsentService;
import org.springframework.stereotype.Service;

/**
 * Single home for inbound keyword routing. STOP/HELP/BAL live here; STATUS, CLAIM
 * and PAY return honest not-yet-available replies behind the S2/S4 seams so the
 * router contract is stable before the Guidewire adapters land.
 */
@Service
public class KeywordRouter {
  private final ConsentService consent;
  private final BalanceProvider balances;

  public KeywordRouter(ConsentService consent, BalanceProvider balances) {
    this.consent = consent;
    this.balances = balances;
  }

  public String route(String from, String body) {
    String keyword = body == null ? "" : body.trim().toUpperCase();
    if (keyword.startsWith("STOP") || keyword.startsWith("UNSUBSCRIBE") || keyword.startsWith("QUIT")
        || keyword.startsWith("END") || keyword.startsWith("CANCEL")) {
      consent.optOut(from, "marketing", "keyword", "twilio");
      consent.optOut(from, "servicing", "keyword", "twilio");
      return "You have been unsubscribed. Reply HELP for help.";
    }
    if (keyword.startsWith("HELP")) {
      return "ETG alerts. Reply STOP to opt out. Help: support@example.com";
    }
    if (keyword.startsWith("BAL")) {
      String balance = balances.balanceFor(from);
      if (balance != null) return balance;
      return "Your balance lookup is not connected yet. An agent will text your balance shortly.";
    }
    if (keyword.startsWith("STATUS")) {
      return "Claim status lookup arrives with the ClaimCenter adapter (S4). An agent will reply shortly.";
    }
    if (keyword.startsWith("CLAIM")) {
      return "Text FNOL arrives with the ClaimCenter adapter (S4). For urgent claims call support@example.com.";
    }
    if (keyword.startsWith("PAY")) {
      return "Pay-by-text arrives with the billing adapter (S2). An agent will send your payment link shortly.";
    }
    return "Thanks — an agent will reply shortly.";
  }
}
