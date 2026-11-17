package com.balancetrail.batch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MappedGatewayLineMapperTest {
  // Txn Ref,Customer Email,Acct No,Amount (INR),Settlement Dt,Status
  private final MappedGatewayLineMapper mapper = new MappedGatewayLineMapper(6, 0, 2, 3, 4, "dd/MM/yyyy");

  @Test
  void picksTheMappedColumnsAndConvertsTheDate() {
    var record = mapper.mapLine("TXN-1001,ravi@example.in,ACC-1001,1250.00,01/07/2026,SUCCESS", 2);

    assertThat(record.parseError()).isNull();
    assertThat(record.transactionId()).isEqualTo("TXN-1001");
    assertThat(record.accountNumber()).isEqualTo("ACC-1001");
    assertThat(record.amount()).isEqualTo("1250.00");
    assertThat(record.transactionDate()).isEqualTo("2026-07-01");
    assertThat(record.lineNumber()).isEqualTo(2);
  }

  @Test
  void aDateInTheWrongFormatNamesTheExpectedFormatAndKeepsTheId() {
    var record = mapper.mapLine("TXN-1001,x,ACC-1001,1250.00,2026-07-01,SUCCESS", 5);

    assertThat(record.parseError()).isEqualTo("Transaction date must use dd/MM/yyyy");
    assertThat(record.transactionId()).isEqualTo("TXN-1001");
  }

  @Test
  void wrongColumnCountIsReported() {
    assertThat(mapper.mapLine("TXN-1001,ACC-1001,1250.00", 3).parseError())
        .isEqualTo("Expected 6 columns but found 3");
  }
}
