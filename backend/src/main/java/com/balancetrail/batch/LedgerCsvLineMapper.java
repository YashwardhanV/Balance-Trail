package com.balancetrail.batch;

import java.util.List;
import org.springframework.batch.item.file.LineMapper;

/** Same idea as {@link GatewayCsvLineMapper}, for the 5-column ledger file. */
public class LedgerCsvLineMapper implements LineMapper<RawLedgerRecord> {

  @Override
  public RawLedgerRecord mapLine(String line, int lineNumber) {
    try {
      List<String> columns = CsvColumns.split(line);
      if (columns.size() != 5) {
        return invalid(lineNumber, "Expected 5 columns but found " + columns.size());
      }
      return new RawLedgerRecord(
          lineNumber,
          CsvColumns.trim(columns.get(0)),
          CsvColumns.trim(columns.get(1)),
          CsvColumns.trim(columns.get(2)),
          CsvColumns.trim(columns.get(3)),
          CsvColumns.trim(columns.get(4)),
          null);
    } catch (IllegalArgumentException exception) {
      return invalid(lineNumber, exception.getMessage());
    }
  }

  private RawLedgerRecord invalid(int lineNumber, String message) {
    return new RawLedgerRecord(lineNumber, null, null, null, null, null, message);
  }
}
