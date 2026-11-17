package com.balancetrail.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.balancetrail.domain.GatewayTransaction;
import com.balancetrail.domain.ItemStatus;
import com.balancetrail.entity.LedgerTransactionEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TransactionMatcherTest {
  private static final LocalDate DAY = LocalDate.of(2026, 8, 1);
  private final TransactionMatcher matcher = new TransactionMatcher(2);

  @Test
  void returnsMatchedWhenAmountsAreNumericallyEqual() {
    var decision = matcher.match(gateway("10.0", "ACC-1", DAY), ledger("10.00", "ACC-1", DAY));

    assertThat(decision.status()).isEqualTo(ItemStatus.MATCHED);
  }

  @Test
  void returnsMismatchAndExplainsBothAmounts() {
    var decision = matcher.match(gateway("10.00", "ACC-1", DAY), ledger("11.00", "ACC-1", DAY));

    assertThat(decision.status()).isEqualTo(ItemStatus.AMOUNT_MISMATCH);
    assertThat(decision.reason()).contains("10.00", "11.00");
  }

  @Test
  void returnsMissingWhenLedgerHasNoTransaction() {
    assertThat(matcher.match(gateway("10.00", "ACC-1", DAY), Optional.empty()).status())
        .isEqualTo(ItemStatus.MISSING_IN_LEDGER);
  }

  @Test
  void sameAmountButDifferentAccountIsAnAccountMismatch() {
    var decision = matcher.match(gateway("10.00", "ACC-9", DAY), ledger("10.00", "ACC-1", DAY));

    assertThat(decision.status()).isEqualTo(ItemStatus.ACCOUNT_MISMATCH);
    assertThat(decision.reason()).contains("ACC-9", "ACC-1");
  }

  @Test
  void datesWithinToleranceStillMatch() {
    var decision =
        matcher.match(gateway("10.00", "ACC-1", DAY.plusDays(2)), ledger("10.00", "ACC-1", DAY));

    assertThat(decision.status()).isEqualTo(ItemStatus.MATCHED);
    assertThat(decision.reason()).contains("2 day(s) apart");
  }

  @Test
  void datesBeyondToleranceAreADateMismatchInEitherDirection() {
    var later =
        matcher.match(gateway("10.00", "ACC-1", DAY.plusDays(3)), ledger("10.00", "ACC-1", DAY));
    var earlier =
        matcher.match(gateway("10.00", "ACC-1", DAY.minusDays(3)), ledger("10.00", "ACC-1", DAY));

    assertThat(later.status()).isEqualTo(ItemStatus.DATE_MISMATCH);
    assertThat(earlier.status()).isEqualTo(ItemStatus.DATE_MISMATCH);
    assertThat(later.reason()).contains("3 days apart", "allowed 2");
  }

  @Test
  void amountWinsOverAccountAndDateButEveryDifferenceIsListed() {
    var decision =
        matcher.match(
            gateway("10.00", "ACC-9", DAY.plusDays(10)), ledger("11.00", "ACC-1", DAY));

    assertThat(decision.status()).isEqualTo(ItemStatus.AMOUNT_MISMATCH);
    assertThat(decision.reason()).contains("amount", "account", "date");
  }

  @Test
  void accountWinsOverDate() {
    var decision =
        matcher.match(gateway("10.00", "ACC-9", DAY.plusDays(10)), ledger("10.00", "ACC-1", DAY));

    assertThat(decision.status()).isEqualTo(ItemStatus.ACCOUNT_MISMATCH);
  }

  @Test
  void zeroToleranceRequiresTheExactDate() {
    var strict = new TransactionMatcher(0);

    var decision =
        strict.match(gateway("10.00", "ACC-1", DAY.plusDays(1)), ledger("10.00", "ACC-1", DAY));

    assertThat(decision.status()).isEqualTo(ItemStatus.DATE_MISMATCH);
  }

  @Test
  void negativeToleranceIsRejectedAtStartup() {
    assertThatThrownBy(() -> new TransactionMatcher(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private GatewayTransaction gateway(String amount, String account, LocalDate date) {
    return new GatewayTransaction(2, "TX-1", account, new BigDecimal(amount), date);
  }

  private Optional<LedgerTransactionEntity> ledger(String amount, String account, LocalDate date) {
    return Optional.of(
        new LedgerTransactionEntity("TX-1", account, new BigDecimal(amount), date, "test"));
  }
}
