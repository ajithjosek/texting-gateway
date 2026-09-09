package com.etg.messaging;

import com.etg.claimcenter.AdjusterSlot;
import com.etg.claimcenter.ClaimCenterClient;
import com.etg.consent.ConsentService;
import com.etg.inbox.InboxService;
import com.etg.salesforce.SalesforceSync;
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
  private final InboxService inbox;
  private final SalesforceSync sync;
  private final ClaimCenterClient claims;

  public KeywordRouter(ConsentService consent, BalanceProvider balances,
                       InboxService inbox, SalesforceSync sync, ClaimCenterClient claims) {
    this.consent = consent;
    this.balances = balances;
    this.inbox = inbox;
    this.sync = sync;
    this.claims = claims;
  }

  public String route(String from, String body) {
    sync.syncContact(from, "servicing");
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
      inbox.noteInbound(from, "servicing", body);
      return "Your balance lookup is not connected yet. An agent will text your balance shortly.";
    }
    if (keyword.startsWith("STATUS")) {
      String[] parts = keyword.split("\\s+");
      if (parts.length < 2) {
        return "Reply STATUS followed by your claim number, e.g. STATUS CLM-1001.";
      }
      String number = parts[1];
      var found = claims.statusOf(number);
      if (found.isEmpty()) {
        inbox.noteInbound(from, "claims", body);
        return "We couldn't find claim " + number + ". An agent will help shortly.";
      }
      var c = found.get();
      return "Claim " + c.number() + ": " + c.status() + ". Adjuster " + c.adjusterName()
          + ", ETA " + c.eta() + ". For inspections reply SCHEDULE " + c.number() + ".";
    }
    if (keyword.startsWith("SCHEDULE")) {
      String[] parts = keyword.split("\\s+");
      if (parts.length < 2) {
        return "Reply SCHEDULE followed by your claim number, e.g. SCHEDULE CLM-1001.";
      }
      String number = parts[1];
      var slots = claims.slotsFor(number);
      if (slots.isEmpty()) {
        inbox.noteInbound(from, "claims", body);
        return "No inspection slots for " + number + " right now. An agent will help shortly.";
      }
      var lines = new StringBuilder("Available inspections for " + number.toUpperCase() + ":");
      for (AdjusterSlot s : slots) {
        lines.append("\n").append(s.id()).append(") ").append(s.label());
      }
      lines.append("\nReply BOOK ").append(number.toUpperCase()).append(" <slot>, e.g. BOOK ")
          .append(number.toUpperCase()).append(" S1.");
      return lines.toString();
    }
    if (keyword.startsWith("BOOK")) {
      String[] parts = keyword.split("\\s+");
      if (parts.length < 3) {
        return "Reply BOOK followed by claim number and slot, e.g. BOOK CLM-1001 S1.";
      }
      var booked = claims.bookSlot(parts[1], parts[2], from);
      if (booked.isEmpty()) {
        inbox.noteInbound(from, "claims", body);
        return "That slot is no longer available. Reply SCHEDULE " + parts[1].toUpperCase()
            + " for current openings, or an agent will help shortly.";
      }
      return "Booked! Confirmation " + booked.get().confirmation()
          + ". Your adjuster will confirm by text.";
    }
    if (keyword.startsWith("CLAIM")) {
      return "Text FNOL arrives with the ClaimCenter adapter (S4). For urgent claims call support@example.com.";
    }
    if (keyword.startsWith("PAY")) {
      inbox.noteInbound(from, "billing", body);
      return "Pay-by-text self-service arrives in S3. An agent will send your payment link shortly.";
    }
    inbox.noteInbound(from, "servicing", body);
    return "Thanks — an agent will reply shortly.";
  }
}
