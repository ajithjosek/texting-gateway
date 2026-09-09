package com.etg.messaging;

/**
 * Billing-balance seam. The Guidewire BillingCenter adapter (S2) implements this;
 * until then the stub keeps BAL honest instead of inventing a balance.
 */
public interface BalanceProvider {
  /** Human-readable balance line, or null when the account is not linked yet. */
  String balanceFor(String phoneE164);
}
