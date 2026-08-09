package com.balancetrail.batch;

import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.BackOffInterruptedException;
import org.springframework.retry.backoff.BackOffPolicy;

public class CalculatedBackOffPolicy implements BackOffPolicy {
  private final RetryDelayCalculator calculator;

  public CalculatedBackOffPolicy(RetryDelayCalculator calculator) {
    this.calculator = calculator;
  }

  @Override
  public BackOffContext start(RetryContext context) {
    return new AttemptContext();
  }

  @Override
  public void backOff(BackOffContext backOffContext) throws BackOffInterruptedException {
    var context = (AttemptContext) backOffContext;
    context.retryNumber++;
    try {
      Thread.sleep(calculator.delayForRetry(context.retryNumber));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new BackOffInterruptedException("Retry wait interrupted", exception);
    }
  }

  private static final class AttemptContext implements BackOffContext {
    private int retryNumber;
  }
}
