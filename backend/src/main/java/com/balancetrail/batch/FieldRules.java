package com.balancetrail.batch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Validation rules shared by the gateway file and the ledger file, so both files follow exactly
 * the same rules for IDs, accounts, amounts and dates.
 */
public final class FieldRules {
  public static final int MAX_ID_LENGTH = 64;
  public static final int MAX_ACCOUNT_LENGTH = 32;

  private FieldRules() {}

  /** Returns a human-readable reason when a field is invalid, or null when all fields are valid. */
  public static String firstError(
      String transactionId, String accountNumber, String amount, String transactionDate) {
    if (isBlank(transactionId)) {
      return "Transaction ID is required";
    }
    if (transactionId.length() > MAX_ID_LENGTH) {
      return "Transaction ID exceeds " + MAX_ID_LENGTH + " characters";
    }
    if (isBlank(accountNumber)) {
      return "Account number is required";
    }
    if (accountNumber.length() > MAX_ACCOUNT_LENGTH) {
      return "Account number exceeds " + MAX_ACCOUNT_LENGTH + " characters";
    }
    BigDecimal parsedAmount = parseAmount(amount);
    if (parsedAmount == null) {
      return "Amount must be a positive number with at most 2 decimals";
    }
    if (parsedAmount.signum() <= 0) {
      return "Amount must be greater than zero";
    }
    if (parseDate(transactionDate) == null) {
      return "Transaction date must use yyyy-MM-dd";
    }
    return null;
  }

  /** Returns the amount with exactly 2 decimals, or null if it is not a number or has more decimals. */
  public static BigDecimal parseAmount(String value) {
    try {
      return new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);
    } catch (RuntimeException exception) {
      return null;
    }
  }

  /** Returns the date, or null if it is missing or not yyyy-MM-dd. */
  public static LocalDate parseDate(String value) {
    try {
      return LocalDate.parse(value);
    } catch (RuntimeException exception) {
      return null;
    }
  }

  public static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
