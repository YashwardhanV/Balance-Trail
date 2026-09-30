package com.balancetrail.batch;

import com.balancetrail.domain.GatewayTransaction;
import com.balancetrail.domain.ItemStatus;
import com.balancetrail.entity.ReconciliationItemEntity;
import com.balancetrail.entity.ReconciliationRunEntity;
import com.balancetrail.repository.LedgerTransactionRepository;
import com.balancetrail.service.TransactionMatcher;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.batch.item.ItemProcessor;

/**
 * Turns one CSV line into exactly one result row. Invalid and duplicate lines are not errors
 * here: they are outcomes the analyst needs to see, so they are returned like any other result.
 */
public class ReconciliationItemProcessor
    implements ItemProcessor<RawGatewayRecord, ReconciliationItemEntity> {

  private final ReconciliationRunEntity run;
  private final DuplicateDetector duplicateDetector;
  private final LedgerTransactionRepository ledgerRepository;
  private final TransactionMatcher matcher;

  public ReconciliationItemProcessor(
      ReconciliationRunEntity run,
      DuplicateDetector duplicateDetector,
      LedgerTransactionRepository ledgerRepository,
      TransactionMatcher matcher) {
    this.run = run;
    this.duplicateDetector = duplicateDetector;
    this.ledgerRepository = ledgerRepository;
    this.matcher = matcher;
  }

  @Override
  public ReconciliationItemEntity process(RawGatewayRecord raw) {
    String error = findValidationError(raw);
    if (error != null) {
      return ReconciliationItemEntity.invalid(
          run, raw.lineNumber(), raw.transactionId(), raw.accountNumber(), error);
    }

    var gateway =
        new GatewayTransaction(
            raw.lineNumber(),
            raw.transactionId(),
            raw.accountNumber(),
            parseAmount(raw.amount()),
            parseDate(raw.transactionDate()));

    if (!duplicateDetector.isFirstOccurrence(gateway.transactionId(), gateway.lineNumber())) {
      return ReconciliationItemEntity.result(
          run,
          gateway,
          null,
          ItemStatus.DUPLICATE,
          "Transaction ID already appeared earlier in this file");
    }

    var ledger = ledgerRepository.findById(gateway.transactionId());
    var decision = matcher.match(gateway, ledger);
    return ReconciliationItemEntity.result(
        run, gateway, ledger.orElse(null), decision.status(), decision.reason());
  }

  /** Returns a human-readable reason when the line is invalid, or null when it is valid. */
  private String findValidationError(RawGatewayRecord raw) {
    if (raw.parseError() != null) {
      return raw.parseError();
    }
    if (isBlank(raw.transactionId())) {
      return "Transaction ID is required";
    }
    if (raw.transactionId().length() > 64) {
      return "Transaction ID exceeds 64 characters";
    }
    if (isBlank(raw.accountNumber())) {
      return "Account number is required";
    }
    if (raw.accountNumber().length() > 32) {
      return "Account number exceeds 32 characters";
    }

    BigDecimal amount = parseAmount(raw.amount());
    if (amount == null) {
      return "Amount must be a positive number with at most 2 decimals";
    }
    if (amount.signum() <= 0) {
      return "Amount must be greater than zero";
    }
    if (parseDate(raw.transactionDate()) == null) {
      return "Transaction date must use yyyy-MM-dd";
    }
    return null;
  }

  private BigDecimal parseAmount(String value) {
    try {
      return new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);
    } catch (RuntimeException exception) {
      return null; // not a number, null, or more than 2 decimal places
    }
  }

  private LocalDate parseDate(String value) {
    try {
      return LocalDate.parse(value);
    } catch (RuntimeException exception) {
      return null; // wrong format or null
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
