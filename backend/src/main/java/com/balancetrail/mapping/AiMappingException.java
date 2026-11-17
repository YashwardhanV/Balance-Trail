package com.balancetrail.mapping;

/** The AI call failed or its answer could not be used. The caller falls back to header rules. */
public class AiMappingException extends RuntimeException {
  public AiMappingException(String message) {
    super(message);
  }

  public AiMappingException(String message, Throwable cause) {
    super(message, cause);
  }
}
