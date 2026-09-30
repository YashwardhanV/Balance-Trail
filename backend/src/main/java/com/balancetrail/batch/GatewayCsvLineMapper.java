package com.balancetrail.batch;

import java.util.ArrayList;
import java.util.List;
import org.springframework.batch.item.file.LineMapper;

/** Maps one physical CSV line. Malformed lines are kept (with a parseError) so they can be reported as INVALID. */
public class GatewayCsvLineMapper implements LineMapper<RawGatewayRecord> {

  @Override
  public RawGatewayRecord mapLine(String line, int lineNumber) {
    try {
      List<String> columns = parseColumns(line);
      if (columns.size() != 4) {
        return invalid(lineNumber, "Expected 4 columns but found " + columns.size());
      }
      return new RawGatewayRecord(
          lineNumber,
          trim(columns.get(0)),
          trim(columns.get(1)),
          trim(columns.get(2)),
          trim(columns.get(3)),
          null);
    } catch (IllegalArgumentException exception) {
      return invalid(lineNumber, exception.getMessage());
    }
  }

  private RawGatewayRecord invalid(int lineNumber, String message) {
    return new RawGatewayRecord(lineNumber, null, null, null, null, message);
  }

  private String trim(String value) {
    return value == null ? null : value.trim();
  }

  static List<String> parseColumns(String line) {
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
}
