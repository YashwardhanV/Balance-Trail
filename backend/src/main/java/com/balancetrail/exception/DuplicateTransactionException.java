package com.balancetrail.exception;

public class DuplicateTransactionException extends RuntimeException {
  public DuplicateTransactionException(String transactionId) {
    super("Duplicate transaction ID in this reconciliation: " + transactionId);
  }
}
