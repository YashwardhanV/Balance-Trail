package com.balancetrail.exception;

/** A request that is well-formed but asks for something invalid (answered with 400). */
public class InvalidRequestException extends RuntimeException {
  public InvalidRequestException(String message) {
    super(message);
  }
}
