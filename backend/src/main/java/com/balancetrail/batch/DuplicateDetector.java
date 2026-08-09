package com.balancetrail.batch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Step-scoped state. Existing IDs are loaded so a restarted step still detects duplicates. */
public class DuplicateDetector {
  private static final long COMMITTED_BEFORE_RESTART = Long.MIN_VALUE;
  private final Map<String, Long> firstLineById;

  public DuplicateDetector(List<String> alreadyProcessedIds) {
    this.firstLineById = new HashMap<>();
    alreadyProcessedIds.forEach(id -> firstLineById.put(id, COMMITTED_BEFORE_RESTART));
  }

  public boolean isFirstOccurrence(String transactionId, long lineNumber) {
    Long firstLine = firstLineById.putIfAbsent(transactionId, lineNumber);
    return firstLine == null || firstLine == lineNumber;
  }
}
