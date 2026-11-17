package com.balancetrail.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ColumnMappingValidatorTest {
  private final List<String> headers = List.of("Txn Ref", "Acct No", "Amount (INR)", "Settlement Dt");

  @Test
  void acceptsRealHeadersAndFixesTheirCase() {
    var result =
        ColumnMappingValidator.clean(
            new ColumnMapping("txn ref", "Acct No", "Amount (INR)", "Settlement Dt"), headers);

    assertThat(result.problems()).isEmpty();
    assertThat(result.mapping()).isEqualTo(new ColumnMapping("Txn Ref", "Acct No", "Amount (INR)", "Settlement Dt"));
  }

  @Test
  void dropsInventedColumnsReusedColumnsAndNone() {
    var result =
        ColumnMappingValidator.clean(
            new ColumnMapping("Payment ID", "Acct No", "Acct No", "NONE"), headers);

    assertThat(result.mapping()).isEqualTo(new ColumnMapping(null, "Acct No", null, null));
    assertThat(result.problems()).hasSize(2);
  }
}
