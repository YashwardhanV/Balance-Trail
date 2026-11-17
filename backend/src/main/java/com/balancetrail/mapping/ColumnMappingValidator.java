package com.balancetrail.mapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks a mapping against the file's real headers. Used on everything that comes back from the
 * AI (never trusted as is) and on what the analyst submits when saving a format.
 */
public final class ColumnMappingValidator {
  public static final String NOT_FOUND = "NONE";

  private ColumnMappingValidator() {}

  public record Result(ColumnMapping mapping, List<String> problems) {}

  /**
   * Keeps each field only if it names one of {@code headers} exactly (case-insensitive matches are
   * corrected to the real spelling) and no other field already uses that header. Everything else
   * becomes null and is reported in {@code problems}.
   */
  public static Result clean(ColumnMapping proposed, List<String> headers) {
    List<String> problems = new ArrayList<>();
    Set<String> used = new HashSet<>();
    String transactionId = check("transaction ID", proposed.transactionId(), headers, used, problems);
    String accountNumber = check("account number", proposed.accountNumber(), headers, used, problems);
    String amount = check("amount", proposed.amount(), headers, used, problems);
    String transactionDate = check("transaction date", proposed.transactionDate(), headers, used, problems);
    return new Result(new ColumnMapping(transactionId, accountNumber, amount, transactionDate), problems);
  }

  private static String check(
      String field, String value, List<String> headers, Set<String> used, List<String> problems) {
    if (value == null || value.isBlank() || NOT_FOUND.equals(value)) {
      return null;
    }
    String header =
        headers.stream().filter(candidate -> candidate.equalsIgnoreCase(value.trim())).findFirst().orElse(null);
    if (header == null) {
      problems.add("The " + field + " column \"" + value + "\" is not a column of this file");
      return null;
    }
    if (!used.add(header)) {
      problems.add("The column \"" + header + "\" was chosen for more than one field");
      return null;
    }
    return header;
  }
}
