package com.etg.messaging;

import org.springframework.stereotype.Component;

/** Placeholder until the S2 BillingCenter adapter lands. */
@Component
public class StubBalanceProvider implements BalanceProvider {
  @Override
  public String balanceFor(String phoneE164) {
    return null;
  }
}
