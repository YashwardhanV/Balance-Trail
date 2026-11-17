package com.balancetrail.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PairSuggesterTest {
  private static final LocalDate DAY = LocalDate.of(2026, 7, 1);
  private final PairSuggester suggester = new PairSuggester(2, 2);

  @Test
  void typoInTheIdWithSameAccountAmountAndDateIsSuggested() {
    var suggestions =
        suggester.suggest(
            List.of(row(1, "TXN-10088", "ACC-1", "910.10", DAY)),
            List.of(row(9, "TXN-1008", "ACC-1", "910.1", DAY.plusDays(1))));

    assertThat(suggestions).containsExactly(new PairSuggester.Suggestion(1, 9, 1, false));
  }

  @Test
  void everyConditionMustHold() {
    var gateway = List.of(row(1, "TXN-1008", "ACC-1", "10.00", DAY));

    assertThat(suggester.suggest(gateway, List.of(row(9, "TXN-1008", "ACC-2", "10.00", DAY)))).isEmpty();
    assertThat(suggester.suggest(gateway, List.of(row(9, "TXN-1008", "ACC-1", "10.01", DAY)))).isEmpty();
    assertThat(suggester.suggest(gateway, List.of(row(9, "TXN-1008", "ACC-1", "10.00", DAY.plusDays(3))))).isEmpty();
    assertThat(suggester.suggest(gateway, List.of(row(9, "TXN-9999", "ACC-1", "10.00", DAY)))).isEmpty();
  }

  @Test
  void idsAreComparedIgnoringCaseAndSurroundingSpaces() {
    var suggestions =
        suggester.suggest(
            List.of(row(1, " txn-1008 ", "ACC-1", "10.00", DAY)),
            List.of(row(9, "TXN-1008", "ACC-1", "10.00", DAY)));

    assertThat(suggestions).extracting(PairSuggester.Suggestion::idDistance).containsExactly(0);
  }

  @Test
  void aRowThatFitsTwoRowsMakesBothPairsAmbiguous() {
    var suggestions =
        suggester.suggest(
            List.of(row(1, "TXN-10081", "ACC-1", "10.00", DAY), row(2, "TXN-10082", "ACC-1", "10.00", DAY)),
            List.of(row(9, "TXN-1008", "ACC-1", "10.00", DAY)));

    assertThat(suggestions).hasSize(2).allMatch(PairSuggester.Suggestion::ambiguous);
  }

  @Test
  void anUnrelatedClearPairStaysUnambiguous() {
    var suggestions =
        suggester.suggest(
            List.of(row(1, "TXN-10081", "ACC-1", "10.00", DAY), row(2, "TXN-10082", "ACC-1", "10.00", DAY),
                row(3, "PAY-77", "ACC-7", "5.00", DAY)),
            List.of(row(9, "TXN-1008", "ACC-1", "10.00", DAY), row(10, "PAY-7", "ACC-7", "5.00", DAY)));

    assertThat(suggestions)
        .filteredOn(suggestion -> suggestion.gatewayItemId() == 3)
        .singleElement()
        .matches(suggestion -> !suggestion.ambiguous());
  }

  private PairSuggester.Row row(long id, String transactionId, String account, String amount, LocalDate date) {
    return new PairSuggester.Row(id, transactionId, account, new BigDecimal(amount), date);
  }
}
