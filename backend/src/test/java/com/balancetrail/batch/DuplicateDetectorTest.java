package com.balancetrail.batch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DuplicateDetectorTest {
  private final DuplicateDetector detector = new DuplicateDetector();

  @Test
  void firstSightingOfAnIdIsNotADuplicate() {
    assertThat(detector.isFirstOccurrence("TX-1", 2)).isTrue();
  }

  @Test
  void sameIdOnALaterLineIsADuplicate() {
    detector.isFirstOccurrence("TX-1", 2);

    assertThat(detector.isFirstOccurrence("TX-1", 5)).isFalse();
  }

  @Test
  void sameLineSeenAgainDuringAChunkRetryIsNotADuplicate() {
    detector.isFirstOccurrence("TX-1", 2);

    assertThat(detector.isFirstOccurrence("TX-1", 2)).isTrue();
  }
}
