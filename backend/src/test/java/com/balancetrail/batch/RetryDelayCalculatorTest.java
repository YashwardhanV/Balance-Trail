package com.balancetrail.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RetryDelayCalculatorTest {

  @Test
  void doublesDelayAndCapsAtMaximum() {
    var calculator = new RetryDelayCalculator(100, 500);

    assertThat(calculator.delayForRetry(1)).isEqualTo(100);
    assertThat(calculator.delayForRetry(2)).isEqualTo(200);
    assertThat(calculator.delayForRetry(3)).isEqualTo(400);
    assertThat(calculator.delayForRetry(4)).isEqualTo(500);
    assertThat(calculator.delayForRetry(20)).isEqualTo(500);
  }

  @Test
  void rejectsInvalidConfigurationAndRetryNumber() {
    assertThatThrownBy(() -> new RetryDelayCalculator(200, 100))
        .isInstanceOf(IllegalArgumentException.class);
    var calculator = new RetryDelayCalculator(100, 500);
    assertThatThrownBy(() -> calculator.delayForRetry(0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
