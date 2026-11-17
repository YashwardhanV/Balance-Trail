package com.balancetrail.exception;

/** The request is valid but cannot run right now (for example, a ledger import is in progress). */
public class ConflictException extends RuntimeException {
  public ConflictException(String message) {
    super(message);
  }
}
