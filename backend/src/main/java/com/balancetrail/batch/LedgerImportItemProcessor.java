package com.balancetrail.batch;

import org.springframework.batch.item.ItemProcessor;

/**
 * Validates one ledger line with the same {@link FieldRules} as the gateway file. A transaction ID
 * that appears twice in one ledger file is rejected on its second line, because it is unclear
 * which of the two rows is right.
 */
public class LedgerImportItemProcessor implements ItemProcessor<RawLedgerRecord, LedgerLine> {
  static final int MAX_DESCRIPTION_LENGTH = 160;
  private static final int MAX_REASON_LENGTH = 500;

  private final DuplicateDetector duplicateDetector;

  public LedgerImportItemProcessor(DuplicateDetector duplicateDetector) {
    this.duplicateDetector = duplicateDetector;
  }

  @Override
  public LedgerLine process(RawLedgerRecord raw) {
    String error =
        raw.parseError() != null
            ? raw.parseError()
            : FieldRules.firstError(
                raw.transactionId(), raw.accountNumber(), raw.amount(), raw.transactionDate());
    if (error == null && raw.description() != null && raw.description().length() > MAX_DESCRIPTION_LENGTH) {
      error = "Description exceeds " + MAX_DESCRIPTION_LENGTH + " characters";
    }
    if (error == null && !duplicateDetector.isFirstOccurrence(raw.transactionId(), raw.lineNumber())) {
      error = "Transaction ID already appeared earlier in this file";
    }
    if (error != null) {
      return new LedgerLine.Invalid(raw.lineNumber(), cut(raw.transactionId(), FieldRules.MAX_ID_LENGTH), cut(error, MAX_REASON_LENGTH));
    }
    return new LedgerLine.Valid(
        raw.lineNumber(),
        raw.transactionId(),
        raw.accountNumber(),
        FieldRules.parseAmount(raw.amount()),
        FieldRules.parseDate(raw.transactionDate()),
        FieldRules.isBlank(raw.description()) ? null : raw.description());
  }

  private static String cut(String value, int maxLength) {
    return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
  }
}
