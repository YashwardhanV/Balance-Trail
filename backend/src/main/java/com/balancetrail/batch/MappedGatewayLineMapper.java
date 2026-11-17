package com.balancetrail.batch;

import com.balancetrail.mapping.DateFormats;
import java.util.List;
import org.springframework.batch.item.file.LineMapper;

/**
 * Reads one line of a gateway file in a saved format: picks the four needed columns by position,
 * ignores the rest, and turns the date into yyyy-MM-dd. Everything after this (validation,
 * duplicates, matching) is exactly the same as for a standard file, and line numbers stay the
 * line numbers of the uploaded file.
 */
public class MappedGatewayLineMapper implements LineMapper<RawGatewayRecord> {
  private final int columnCount;
  private final int transactionIdIndex;
  private final int accountNumberIndex;
  private final int amountIndex;
  private final int transactionDateIndex;
  private final String dateFormat;

  public MappedGatewayLineMapper(
      int columnCount,
      int transactionIdIndex,
      int accountNumberIndex,
      int amountIndex,
      int transactionDateIndex,
      String dateFormat) {
    this.columnCount = columnCount;
    this.transactionIdIndex = transactionIdIndex;
    this.accountNumberIndex = accountNumberIndex;
    this.amountIndex = amountIndex;
    this.transactionDateIndex = transactionDateIndex;
    this.dateFormat = dateFormat;
  }

  @Override
  public RawGatewayRecord mapLine(String line, int lineNumber) {
    List<String> columns;
    try {
      columns = CsvColumns.split(line);
    } catch (IllegalArgumentException exception) {
      return invalid(lineNumber, exception.getMessage());
    }
    if (columns.size() != columnCount) {
      return invalid(lineNumber, "Expected " + columnCount + " columns but found " + columns.size());
    }
    String transactionId = CsvColumns.trim(columns.get(transactionIdIndex));
    String accountNumber = CsvColumns.trim(columns.get(accountNumberIndex));
    String amount = CsvColumns.trim(columns.get(amountIndex));
    String rawDate = CsvColumns.trim(columns.get(transactionDateIndex));
    var date = DateFormats.parse(rawDate, dateFormat);
    if (date.isEmpty()) {
      return new RawGatewayRecord(
          lineNumber, transactionId, accountNumber, amount, rawDate,
          "Transaction date must use " + dateFormat);
    }
    return new RawGatewayRecord(lineNumber, transactionId, accountNumber, amount, date.get().toString(), null);
  }

  private RawGatewayRecord invalid(int lineNumber, String message) {
    return new RawGatewayRecord(lineNumber, null, null, null, null, message);
  }
}
