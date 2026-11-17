package com.balancetrail.mapping;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Works out the date format from real sample values, locally. The AI is never asked about dates:
 * this is plain rule work, and the AI only sees masked values anyway.
 */
public final class DateFormatDetector {
  private DateFormatDetector() {}

  /**
   * Returns every allowed format that fits the samples, best first, keeping only one format per
   * distinct reading of the dates (dd/MM/yyyy and d/M/yyyy read "09/07/2026" the same way, so only
   * the first is kept). More than one result means the samples are ambiguous, e.g. every day is
   * 12 or less, and the analyst has to choose.
   *
   * <p>"Fits" allows a few stray bad values, because real files contain a bad row now and then and
   * one bad row in the sample should not stop detection: with 3 or more samples, up to 1 in 10
   * (at least 1) may fail. Those lines will be reported INVALID when the file is reconciled.
   * The tolerance never creates ambiguity: if some format fits every sample, only formats that
   * fit every sample are returned ("13/07/2026" alone rules out MM/dd/yyyy).
   */
  public static List<String> candidates(List<String> samples) {
    List<String> values = samples.stream().filter(value -> value != null && !value.isBlank()).toList();
    if (values.isEmpty()) {
      return List.of();
    }
    int allowedFailures = values.size() >= 3 ? Math.max(1, values.size() / 10) : 0;

    record Candidate(String pattern, int failures, int order) {}
    List<Candidate> candidates = new ArrayList<>();
    Set<List<LocalDate>> readingsSeen = new HashSet<>();
    List<String> patterns = DateFormats.all();
    for (int order = 0; order < patterns.size(); order++) {
      String pattern = patterns.get(order);
      List<LocalDate> reading = new ArrayList<>(values.size());
      int failures = 0;
      for (String value : values) {
        LocalDate date = DateFormats.parse(value, pattern).orElse(null);
        if (date == null) {
          failures++;
        }
        reading.add(date);
      }
      if (failures <= allowedFailures && readingsSeen.add(reading)) {
        candidates.add(new Candidate(pattern, failures, order));
      }
    }
    int fewestFailures = candidates.stream().mapToInt(Candidate::failures).min().orElse(0);
    return candidates.stream()
        .filter(candidate -> candidate.failures() == fewestFailures)
        .sorted(Comparator.comparingInt(Candidate::order))
        .map(Candidate::pattern)
        .toList();
  }

  /** How many samples do not fit {@code pattern}. */
  public static long failures(List<String> samples, String pattern) {
    return samples.stream()
        .filter(value -> value != null && !value.isBlank())
        .filter(value -> DateFormats.parse(value, pattern).isEmpty())
        .count();
  }
}
