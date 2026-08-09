package com.balancetrail.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.balancetrail.domain.GatewayTransaction;
import com.balancetrail.domain.ItemStatus;
import com.balancetrail.entity.LedgerTransactionEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TransactionMatcherTest {
  private final TransactionMatcher matcher = new TransactionMatcher();

  @Test
  void returnsMatchedWhenAmountsAreNumericallyEqual() {
    var gateway = gateway("10.0");
    var ledger = ledger("10.00");

    assertThat(matcher.match(gateway, Optional.of(ledger)).status())
        .isEqualTo(ItemStatus.MATCHED);
  }

  @Test
  void returnsMismatchAndExplainsBothAmounts() {
    var decision = matcher.match(gateway("10.00"), Optional.of(ledger("11.00")));

    assertThat(decision.status()).isEqualTo(ItemStatus.AMOUNT_MISMATCH);
    assertThat(decision.reason()).contains("10.00", "11.00");
  }

  @Test
  void returnsMissingWhenLedgerHasNoTransaction() {
    assertThat(matcher.match(gateway("10.00"), Optional.empty()).status())
        .isEqualTo(ItemStatus.MISSING_IN_LEDGER);
  }

  private GatewayTransaction gateway(String amount) {
    return new GatewayTransaction(
        2, "TX-1", "ACC-1", new BigDecimal(amount), LocalDate.of(2026, 8, 1));
  }

  private LedgerTransactionEntity ledger(String amount) {
    return new LedgerTransactionEntity(
        "TX-1",
        "ACC-1",
        new BigDecimal(amount),
        LocalDate.of(2026, 8, 1),
        "test");
  }
}
