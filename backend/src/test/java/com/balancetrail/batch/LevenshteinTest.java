package com.balancetrail.batch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LevenshteinTest {

  @Test
  void countsInsertsDeletesAndReplacements() {
    assertThat(Levenshtein.distance("TXN-1008", "TXN-1008", 5)).isZero();
    assertThat(Levenshtein.distance("TXN-1008", "TXN-10088", 5)).isEqualTo(1);
    assertThat(Levenshtein.distance("TXN-1008", "TXN-1009", 5)).isEqualTo(1);
    assertThat(Levenshtein.distance("TXN-1008", "TXN-1080", 5)).isEqualTo(2);
    assertThat(Levenshtein.distance("", "abc", 5)).isEqualTo(3);
  }

  @Test
  void stopsEarlyOnceTheLimitIsPassed() {
    assertThat(Levenshtein.distance("TXN-1008", "PAYMENT-REFERENCE-77", 2)).isEqualTo(3);
    assertThat(Levenshtein.distance("ABCDEFGH", "HGFEDCBA", 2)).isEqualTo(3);
  }
}
