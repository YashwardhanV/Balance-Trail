package com.balancetrail.batch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LedgerImportItemProcessorTest {
  private final LedgerImportItemProcessor processor =
      new LedgerImportItemProcessor(new DuplicateDetector());
  private final LedgerCsvLineMapper mapper = new LedgerCsvLineMapper();

  @Test
  void validLineBecomesALedgerRow() {
    var line = processor.process(mapper.mapLine("TX-1, ACC-1 ,10.5,2026-08-01,Card settlement", 2));

    assertThat(line).isInstanceOf(LedgerLine.Valid.class);
    var valid = (LedgerLine.Valid) line;
    assertThat(valid.accountNumber()).isEqualTo("ACC-1");
    assertThat(valid.amount()).isEqualByComparingTo("10.50");
    assertThat(valid.description()).isEqualTo("Card settlement");
  }

  @Test
  void emptyDescriptionIsStoredAsNull() {
    var line = (LedgerLine.Valid) processor.process(mapper.mapLine("TX-1,ACC-1,10.00,2026-08-01,", 2));

    assertThat(line.description()).isNull();
  }

  @Test
  void usesTheSameFieldRulesAsTheGatewayFile() {
    var line = processor.process(mapper.mapLine("TX-1,ACC-1,-5.00,2026-08-01,", 2));

    assertThat(line).isInstanceOf(LedgerLine.Invalid.class);
    assertThat(((LedgerLine.Invalid) line).reason()).isEqualTo("Amount must be greater than zero");
  }

  @Test
  void wrongColumnCountIsRejected() {
    var line = processor.process(mapper.mapLine("TX-1,ACC-1,10.00,2026-08-01", 2));

    assertThat(((LedgerLine.Invalid) line).reason()).isEqualTo("Expected 5 columns but found 4");
  }

  @Test
  void overLongDescriptionIsRejected() {
    String longText = "x".repeat(LedgerImportItemProcessor.MAX_DESCRIPTION_LENGTH + 1);

    var line = processor.process(mapper.mapLine("TX-1,ACC-1,10.00,2026-08-01," + longText, 2));

    assertThat(((LedgerLine.Invalid) line).reason()).contains("Description exceeds");
  }

  @Test
  void secondLineWithTheSameIdIsRejected() {
    processor.process(mapper.mapLine("TX-1,ACC-1,10.00,2026-08-01,", 2));

    var second = processor.process(mapper.mapLine("TX-1,ACC-1,12.00,2026-08-01,", 3));

    assertThat(second).isInstanceOf(LedgerLine.Invalid.class);
    assertThat(((LedgerLine.Invalid) second).reason()).contains("already appeared");
  }
}
