package com.balancetrail.domain;

/**
 * The outcome recorded for one reconciliation result.
 *
 * <p>When a gateway row differs from its ledger row in more than one field, the most serious
 * difference wins: amount, then account, then date. The reason text still lists every difference.
 */
public enum ItemStatus {
  MATCHED,
  AMOUNT_MISMATCH,
  ACCOUNT_MISMATCH,
  DATE_MISMATCH,
  MISSING_IN_LEDGER,
  /** A ledger row dated inside the file's date range that the gateway file never mentions. */
  MISSING_IN_GATEWAY,
  INVALID,
  DUPLICATE
}
