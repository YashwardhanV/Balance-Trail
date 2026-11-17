package com.balancetrail.mapping;

import java.util.regex.Pattern;

/**
 * Hides values before anything is sent to an AI service. Every digit becomes 9 and every letter
 * becomes a/A, keeping only the SHAPE: "ACC-100234" → "AAA-999999", "09/07/2026" → "99/99/9999",
 * "ravi@x.in" → "aaaa@a.aa". The shape is enough to tell a date column from an amount or an ID;
 * the real account numbers, emails and amounts never leave the server. (India's DPDP Act makes
 * sending customers' personal data to a third party something to avoid by default.)
 */
public final class SampleMasker {
  private static final int MAX_LENGTH = 40;
  private static final Pattern LOOKS_LIKE_DATA = Pattern.compile(".*\\d{5,}.*");

  private SampleMasker() {}

  public static String mask(String value) {
    if (value == null) {
      return "";
    }
    String text = value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
    StringBuilder masked = new StringBuilder(text.length());
    for (char character : text.toCharArray()) {
      if (Character.isDigit(character)) {
        masked.append('9');
      } else if (Character.isLetter(character)) {
        masked.append(Character.isUpperCase(character) ? 'A' : 'a');
      } else {
        masked.append(character);
      }
    }
    return masked.toString();
  }

  /**
   * Header names are sent as they are, because the AI needs to read them. But if the first line
   * is actually data (a file without a header row), it could hold an account number: a "header"
   * with 5 or more digits in a row is masked like a value.
   */
  public static String maskHeaderIfItLooksLikeData(String header) {
    return LOOKS_LIKE_DATA.matcher(header).matches() ? mask(header) : header;
  }

  public static boolean looksLikeData(String header) {
    return LOOKS_LIKE_DATA.matcher(header).matches();
  }
}
