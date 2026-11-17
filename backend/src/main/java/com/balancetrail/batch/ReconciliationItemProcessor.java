package com.balancetrail.batch;

import com.balancetrail.domain.GatewayTransaction;
import com.balancetrail.domain.ItemStatus;
import com.balancetrail.entity.ReconciliationItemEntity;
import com.balancetrail.entity.ReconciliationRunEntity;
import org.springframework.batch.item.ItemProcessor;

/**
 * Validates one CSV line and checks it for duplicates. It does not touch the database: the ledger
 * lookup happens later, once per chunk, in {@link LedgerMatchingItemWriter}.
 */
public class ReconciliationItemProcessor implements ItemProcessor<RawGatewayRecord, ProcessedLine> {

  private final ReconciliationRunEntity run;
  private final DuplicateDetector duplicateDetector;

  public ReconciliationItemProcessor(
      ReconciliationRunEntity run, DuplicateDetector duplicateDetector) {
    this.run = run;
    this.duplicateDetector = duplicateDetector;
  }

  @Override
  public ProcessedLine process(RawGatewayRecord raw) {
    String error =
        raw.parseError() != null
            ? raw.parseError()
            : FieldRules.firstError(
                raw.transactionId(), raw.accountNumber(), raw.amount(), raw.transactionDate());
    if (error != null) {
      return new ProcessedLine.Decided(
          ReconciliationItemEntity.invalid(
              run, raw.lineNumber(), raw.transactionId(), raw.accountNumber(), error));
    }

    var gateway =
        new GatewayTransaction(
            raw.lineNumber(),
            raw.transactionId(),
            raw.accountNumber(),
            FieldRules.parseAmount(raw.amount()),
            FieldRules.parseDate(raw.transactionDate()));

    if (!duplicateDetector.isFirstOccurrence(gateway.transactionId(), gateway.lineNumber())) {
      return new ProcessedLine.Decided(
          ReconciliationItemEntity.result(
              run,
              gateway,
              null,
              ItemStatus.DUPLICATE,
              "Transaction ID already appeared earlier in this file"));
    }
    return new ProcessedLine.AwaitingLedger(gateway);
  }
}
