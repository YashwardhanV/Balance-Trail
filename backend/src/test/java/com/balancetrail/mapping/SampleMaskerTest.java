package com.balancetrail.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SampleMaskerTest {

  @Test
  void keepsOnlyTheShapeOfAValue() {
    assertThat(SampleMasker.mask("ACC-100234")).isEqualTo("AAA-999999");
    assertThat(SampleMasker.mask("ravi.k@bank.in")).isEqualTo("aaaa.a@aaaa.aa");
    assertThat(SampleMasker.mask("1250.00")).isEqualTo("9999.99");
    assertThat(SampleMasker.mask("09/07/2026")).isEqualTo("99/99/9999");
    assertThat(SampleMasker.mask(null)).isEmpty();
  }

  @Test
  void cutsLongValues() {
    assertThat(SampleMasker.mask("x".repeat(100))).hasSize(40);
  }

  @Test
  void aHeaderThatLooksLikeAnAccountNumberIsMaskedToo() {
    assertThat(SampleMasker.maskHeaderIfItLooksLikeData("Txn Ref")).isEqualTo("Txn Ref");
    assertThat(SampleMasker.maskHeaderIfItLooksLikeData("50100234567")).isEqualTo("99999999999");
  }
}
