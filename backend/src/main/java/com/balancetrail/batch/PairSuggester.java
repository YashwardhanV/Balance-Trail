package com.balancetrail.batch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finds "probably the same payment" pairs between the two kinds of leftovers of a run:
 * a gateway line the ledger doesn't know (MISSING_IN_LEDGER) and a ledger row the file
 * doesn't mention (MISSING_IN_GATEWAY).
 *
 * <p>A pair is suggested only when ALL of these hold:
 *
 * <ul>
 *   <li>same account number and same amount,
 *   <li>dates within the matching tolerance,
 *   <li>transaction IDs at most {@code maxIdDistance} characters apart (a typo, not a different ID).
 * </ul>
 *
 * <p>No guessing: if a row could pair with more than one row on the other side, every pair it is
 * part of is marked ambiguous and the analyst decides. Nothing is ever confirmed automatically.
 *
 * <p>Cost: rows are grouped by (account, amount) first, so only rows in the same group are compared.
 * One-to-many matches (one ledger row paid as several gateway rows) are deliberately not attempted.
 */
public class PairSuggester {
  public record Row(long itemId, String transactionId, String accountNumber, BigDecimal amount, LocalDate date) {}

  public record Suggestion(long gatewayItemId, long ledgerItemId, int idDistance, boolean ambiguous) {}

  private final int dateToleranceDays;
  private final int maxIdDistance;

  public PairSuggester(int dateToleranceDays, int maxIdDistance) {
    this.dateToleranceDays = dateToleranceDays;
    this.maxIdDistance = maxIdDistance;
  }

  public List<Suggestion> suggest(List<Row> missingInLedger, List<Row> missingInGateway) {
    Map<String, List<Row>> ledgerByKey = new HashMap<>();
    for (Row ledgerRow : missingInGateway) {
      ledgerByKey.computeIfAbsent(key(ledgerRow), ignored -> new ArrayList<>()).add(ledgerRow);
    }

    List<long[]> pairs = new ArrayList<>(); // {gatewayItemId, ledgerItemId, distance}
    Map<Long, Integer> pairsPerGatewayRow = new HashMap<>();
    Map<Long, Integer> pairsPerLedgerRow = new HashMap<>();
    for (Row gatewayRow : missingInLedger) {
      for (Row ledgerRow : ledgerByKey.getOrDefault(key(gatewayRow), List.of())) {
        if (!datesClose(gatewayRow.date(), ledgerRow.date())) {
          continue;
        }
        int distance =
            Levenshtein.distance(normalize(gatewayRow.transactionId()), normalize(ledgerRow.transactionId()), maxIdDistance);
        if (distance > maxIdDistance) {
          continue;
        }
        pairs.add(new long[] {gatewayRow.itemId(), ledgerRow.itemId(), distance});
        pairsPerGatewayRow.merge(gatewayRow.itemId(), 1, Integer::sum);
        pairsPerLedgerRow.merge(ledgerRow.itemId(), 1, Integer::sum);
      }
    }

    List<Suggestion> suggestions = new ArrayList<>(pairs.size());
    for (long[] pair : pairs) {
      boolean ambiguous = pairsPerGatewayRow.get(pair[0]) > 1 || pairsPerLedgerRow.get(pair[1]) > 1;
      suggestions.add(new Suggestion(pair[0], pair[1], (int) pair[2], ambiguous));
    }
    return suggestions;
  }

  private boolean datesClose(LocalDate gateway, LocalDate ledger) {
    return Math.abs(ChronoUnit.DAYS.between(ledger, gateway)) <= dateToleranceDays;
  }

  /** 10.5 and 10.50 must land in the same group. */
  private static String key(Row row) {
    return row.accountNumber() + "|" + row.amount().stripTrailingZeros().toPlainString();
  }

  private static String normalize(String transactionId) {
    return transactionId.trim().toUpperCase(Locale.ROOT);
  }
}
