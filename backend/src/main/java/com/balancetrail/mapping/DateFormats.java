package com.balancetrail.mapping;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The date formats a gateway file may use. A fixed allow-list: a format name typed by a user or
 * returned by an AI is only accepted if it is one of these, so nothing outside the list ever
 * reaches {@link DateTimeFormatter#ofPattern}.
 *
 * <p>The order matters when samples fit more than one format (01/07/2026 is 1 July as dd/MM and
 * 7 January as MM/dd): the earlier one is proposed first, and dd/MM comes before MM/dd because
 * Indian gateways use day-first dates.
 */
public final class DateFormats {
  private static final Map<String, DateTimeFormatter> FORMATS = new LinkedHashMap<>();

  static {
    add("yyyy-MM-dd");
    add("dd/MM/yyyy");
    add("dd-MM-yyyy");
    add("dd.MM.yyyy");
    add("d/M/yyyy");
    add("dd-MMM-yyyy");
    add("d MMM yyyy");
    add("yyyy/MM/dd");
    add("yyyyMMdd");
    add("MM/dd/yyyy");
    add("M/d/yyyy");
    add("MM-dd-yyyy");
    add("yyyy-MM-dd HH:mm:ss");
    add("dd/MM/yyyy HH:mm:ss");
    add("dd-MM-yyyy HH:mm:ss");
    add("MM/dd/yyyy HH:mm:ss");
  }

  private DateFormats() {}

  private static void add(String pattern) {
    // 'uuuu' instead of 'yyyy' so STRICT parsing works without an era; STRICT rejects 31/02.
    FORMATS.put(
        pattern,
        new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern(pattern.replace("yyyy", "uuuu"))
            .toFormatter(Locale.ENGLISH)
            .withResolverStyle(ResolverStyle.STRICT));
  }

  public static List<String> all() {
    return List.copyOf(FORMATS.keySet());
  }

  public static boolean isAllowed(String pattern) {
    return pattern != null && FORMATS.containsKey(pattern);
  }

  /** The date in {@code value}, or empty if it does not match {@code pattern} exactly. */
  public static Optional<LocalDate> parse(String value, String pattern) {
    DateTimeFormatter formatter = FORMATS.get(pattern);
    if (formatter == null || value == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(formatter.parse(value.trim(), LocalDate::from));
    } catch (RuntimeException exception) {
      return Optional.empty();
    }
  }
}
