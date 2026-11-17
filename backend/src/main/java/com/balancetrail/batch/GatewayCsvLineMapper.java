package com.balancetrail.batch;

import java.util.List;
import org.springframework.batch.item.file.LineMapper;

/** Maps one physical CSV line. Malformed lines are kept (with a parseError) so they can be reported as INVALID. */
public class GatewayCsvLineMapper implements LineMapper<RawGatewayRecord> {

  @Override
  public RawGatewayRecord mapLine(String line, int lineNumber) {
    try {
      List<String> columns = CsvColumns.split(line);
      if (columns.size() != 4) {
        return invalid(lineNumber, "Expected 4 columns but found " + columns.size());
      }
      return new RawGatewayRecord(
          lineNumber,
          CsvColumns.trim(columns.get(0)),
          CsvColumns.trim(columns.get(1)),
          CsvColumns.trim(columns.get(2)),
          CsvColumns.trim(columns.get(3)),
          null);
    } catch (IllegalArgumentException exception) {
      return invalid(lineNumber, exception.getMessage());
    }
  }

  private RawGatewayRecord invalid(int lineNumber, String message) {
    return new RawGatewayRecord(lineNumber, null, null, null, null, message);
  }
}
