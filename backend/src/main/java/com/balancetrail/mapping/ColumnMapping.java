package com.balancetrail.mapping;

/**
 * Which header of an uploaded file holds each of the four fields reconciliation needs.
 * A null field means "not found yet"; the analyst must fill it before the format can be saved.
 */
public record ColumnMapping(
    String transactionId, String accountNumber, String amount, String transactionDate) {

  public static final ColumnMapping EMPTY = new ColumnMapping(null, null, null, null);

  public boolean isComplete() {
    return transactionId != null && accountNumber != null && amount != null && transactionDate != null;
  }

  /** Keeps this mapping's fields and fills only the missing ones from {@code other}. */
  public ColumnMapping fillMissingFrom(ColumnMapping other) {
    return new ColumnMapping(
        transactionId != null ? transactionId : other.transactionId,
        accountNumber != null ? accountNumber : other.accountNumber,
        amount != null ? amount : other.amount,
        transactionDate != null ? transactionDate : other.transactionDate);
  }
}
