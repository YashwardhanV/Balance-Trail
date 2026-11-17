package com.balancetrail.batch;

import com.balancetrail.domain.GatewayTransaction;
import com.balancetrail.entity.ReconciliationItemEntity;

/**
 * What the processor hands to the writer for one CSV line.
 *
 * <p>Invalid and duplicate lines are already decided. Valid lines still need their ledger row;
 * the writer looks those up for the whole chunk in one query instead of one query per line.
 */
public sealed interface ProcessedLine {

  /** The outcome is already known (INVALID or DUPLICATE). */
  record Decided(ReconciliationItemEntity item) implements ProcessedLine {}

  /** A valid line that must be compared with the ledger. */
  record AwaitingLedger(GatewayTransaction gateway) implements ProcessedLine {}
}
