package com.balancetrail.batch;

import com.balancetrail.domain.GatewayTransaction;
import com.balancetrail.entity.ReconciliationItemEntity;
import com.balancetrail.entity.ReconciliationRunEntity;
import com.balancetrail.exception.DuplicateTransactionException;
import com.balancetrail.exception.InvalidRecordException;
import com.balancetrail.repository.LedgerTransactionRepository;
import com.balancetrail.service.TransactionMatcher;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.batch.item.ItemProcessor;

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
    GatewayTransaction gateway = validate(raw);
    if (!duplicateDetector.isFirstOccurrence(gateway.transactionId(), gateway.lineNumber())) {
      throw new DuplicateTransactionException(gateway.transactionId());
    }

    var ledger = ledgerRepository.findById(gateway.transactionId());
    var decision = matcher.match(gateway, ledger);
    return ReconciliationItemEntity.result(
        run,
        gateway.lineNumber(),
        gateway.transactionId(),
        gateway.accountNumber(),
        gateway.amount(),
        gateway.transactionDate(),
        ledger.orElse(null),
        decision.status(),
        decision.reason());
  }

  private GatewayTransaction validate(RawGatewayRecord raw) {
    if (raw.parseError() != null) {
      throw new InvalidRecordException(raw.parseError());
    }
    if (isBlank(raw.transactionId())) {
      throw new InvalidRecordException("Transaction ID is required");
    }
    if (raw.transactionId().length() > 64) {
      throw new InvalidRecordException("Transaction ID exceeds 64 characters");
    }
    if (isBlank(raw.accountNumber())) {
      throw new InvalidRecordException("Account number is required");
    }
    if (raw.accountNumber().length() > 32) {
      throw new InvalidRecordException("Account number exceeds 32 characters");
    }

    BigDecimal amount;
    try {
      amount = new BigDecimal(raw.amount()).setScale(2, RoundingMode.UNNECESSARY);
    } catch (RuntimeException exception) {
      throw new InvalidRecordException("Amount must be a positive number with at most 2 decimals");
    }
    if (amount.signum() <= 0) {
      throw new InvalidRecordException("Amount must be greater than zero");
    }

    LocalDate date;
    try {
      date = LocalDate.parse(raw.transactionDate());
    } catch (DateTimeParseException | NullPointerException exception) {
      throw new InvalidRecordException("Transaction date must use yyyy-MM-dd");
    }

    return new GatewayTransaction(
        raw.lineNumber(), raw.transactionId(), raw.accountNumber(), amount, date);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
