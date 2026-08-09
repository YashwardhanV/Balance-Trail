package com.balancetrail.batch;

public class RetryDelayCalculator {
  private final long initialDelayMs;
  private final long maxDelayMs;

  public RetryDelayCalculator(long initialDelayMs, long maxDelayMs) {
    if (initialDelayMs < 0 || maxDelayMs < initialDelayMs) {
      throw new IllegalArgumentException("Retry delays must satisfy 0 <= initial <= maximum");
    }
    this.initialDelayMs = initialDelayMs;
    this.maxDelayMs = maxDelayMs;
  }

  public long delayForRetry(int retryNumber) {
    if (retryNumber < 1) {
      throw new IllegalArgumentException("Retry number starts at 1");
    }
    long delay = initialDelayMs;
    for (int attempt = 1; attempt < retryNumber && delay < maxDelayMs; attempt++) {
      delay = Math.min(maxDelayMs, delay > Long.MAX_VALUE / 2 ? maxDelayMs : delay * 2);
    }
    return delay;
  }
}
