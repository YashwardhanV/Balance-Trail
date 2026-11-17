package com.balancetrail.batch;

import java.util.ArrayList;
import java.util.List;

/** Splits one CSV line into columns. Handles quoted fields and escaped quotes ("" inside quotes). */
public final class CsvColumns {
  private CsvColumns() {}

  /** Returns the columns of the line, or throws IllegalArgumentException for an unclosed quote. */
  public static List<String> split(String line) {
    List<String> columns = new ArrayList<>();
    StringBuilder value = new StringBuilder();
    boolean quoted = false;

    for (int index = 0; index < line.length(); index++) {
      char current = line.charAt(index);
      if (current == '"') {
        if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
          value.append('"');
          index++;
        } else {
          quoted = !quoted;
        }
      } else if (current == ',' && !quoted) {
        columns.add(value.toString());
        value.setLength(0);
      } else {
        value.append(current);
      }
    }

    if (quoted) {
      throw new IllegalArgumentException("Unclosed quoted field");
    }
    columns.add(value.toString());
    return columns;
  }

  static String trim(String value) {
    return value == null ? null : value.trim();
  }
}
