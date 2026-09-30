package com.balancetrail.batch;

import java.util.HashMap;
import java.util.Map;

/**
 * Remembers the first line on which each transaction ID appeared in the current file.
 *
 * <p>A later line with the same ID is a duplicate. The same line seen again is not: if a chunk is
 * retried after a database hiccup, Spring Batch processes its rows a second time.
 */
public class DuplicateDetector {
  private final Map<String, Long> firstLineById = new HashMap<>();

  public boolean isFirstOccurrence(String transactionId, long lineNumber) {
    Long firstLine = firstLineById.putIfAbsent(transactionId, lineNumber);
    return firstLine == null || firstLine == lineNumber;
  }
}
