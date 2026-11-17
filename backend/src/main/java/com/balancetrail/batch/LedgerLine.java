package com.balancetrail.batch;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One processed line of a ledger file: either a row to save or a reason it was rejected. */
public sealed interface LedgerLine {
  long lineNumber();

  record Valid(
      long lineNumber,
      String transactionId,
      String accountNumber,
      BigDecimal amount,
      LocalDate transactionDate,
      String description)
      implements LedgerLine {}

  record Invalid(long lineNumber, String transactionId, String reason) implements LedgerLine {}
}
