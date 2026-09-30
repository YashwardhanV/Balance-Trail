package com.balancetrail.batch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GatewayCsvLineMapperTest {
  private final GatewayCsvLineMapper mapper = new GatewayCsvLineMapper();

  @Test
  void parsesQuotedFieldsAndEscapedQuotes() {
    var record = mapper.mapLine("TX-1,\"ACC,100\",10.00,2026-08-01", 2);

    assertThat(record.transactionId()).isEqualTo("TX-1");
    assertThat(record.accountNumber()).isEqualTo("ACC,100");
    assertThat(record.parseError()).isNull();
  }

  @Test
  void keepsMalformedRowsWithAParseError() {
    var wrongColumns = mapper.mapLine("TX-1,ACC-1,10.00", 3);
    var unclosedQuote = mapper.mapLine("\"TX-1,ACC-1,10.00,2026-08-01", 4);

    assertThat(wrongColumns.parseError()).contains("Expected 4 columns");
    assertThat(unclosedQuote.parseError()).isEqualTo("Unclosed quoted field");
  }
}
