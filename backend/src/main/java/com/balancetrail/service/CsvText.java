package com.balancetrail.service;

/** Turns values into safe CSV cells for the discrepancy export. */
final class CsvText {
  private CsvText() {}

  /**
   * Quotes a value when it contains a comma, quote or line break. Text that a spreadsheet would
   * run as a formula (starting with = + - @ tab or CR) gets a leading apostrophe first, because
   * IDs in the export come from an uploaded file that anyone could have edited.
   */
  static String cell(Object value) {
    if (value == null) {
      return "";
    }
    String text = value.toString();
    if (!(value instanceof Number) && !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
      text = "'" + text;
    }
    if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
      text = "\"" + text.replace("\"", "\"\"") + "\"";
    }
    return text;
  }
}
