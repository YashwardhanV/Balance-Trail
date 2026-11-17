package com.balancetrail.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.balancetrail.domain.ItemStatus;
import com.balancetrail.entity.ReconciliationRunEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReconciliationItemProcessorTest {
  private ReconciliationItemProcessor processor;

  @BeforeEach
  void setUp() {
    processor =
        new ReconciliationItemProcessor(mock(ReconciliationRunEntity.class), new DuplicateDetector());
  }

  @Test
  void validLineWaitsForTheLedgerLookupWithParsedValues() {
    var result = processor.process(raw(2, "TX-1", "10.5", "2026-08-01"));

    assertThat(result).isInstanceOf(ProcessedLine.AwaitingLedger.class);
    var gateway = ((ProcessedLine.AwaitingLedger) result).gateway();
    assertThat(gateway.amount()).isEqualByComparingTo("10.50");
    assertThat(gateway.transactionDate()).hasToString("2026-08-01");
  }

  @Test
  void returnsInvalidResultForMalformedAmount() {
    var item = decided(processor.process(raw(2, "TX-1", "10.001", "2026-08-01")));

    assertThat(item.getStatus()).isEqualTo(ItemStatus.INVALID);
    assertThat(item.getReason()).contains("at most 2 decimals");
  }

  @Test
  void returnsInvalidResultForBadDate() {
    var item = decided(processor.process(raw(2, "TX-1", "10.00", "01-08-2026")));

    assertThat(item.getStatus()).isEqualTo(ItemStatus.INVALID);
    assertThat(item.getReason()).contains("yyyy-MM-dd");
  }

  @Test
  void keepsTheParseErrorOfABrokenLine() {
    var broken = new RawGatewayRecord(4, null, null, null, null, "Unclosed quoted field");

    assertThat(decided(processor.process(broken)).getReason()).isEqualTo("Unclosed quoted field");
  }

  @Test
  void samePhysicalLineCanBeReprocessedAfterAChunkRollback() {
    var input = raw(2, "TX-1", "10.00", "2026-08-01");

    assertThat(processor.process(input)).isInstanceOf(ProcessedLine.AwaitingLedger.class);
    assertThat(processor.process(input)).isInstanceOf(ProcessedLine.AwaitingLedger.class);
  }

  @Test
  void laterPhysicalLineWithSameIdIsDuplicate() {
    processor.process(raw(2, "TX-1", "10.00", "2026-08-01"));

    var later = decided(processor.process(raw(3, "TX-1", "10.00", "2026-08-01")));

    assertThat(later.getStatus()).isEqualTo(ItemStatus.DUPLICATE);
  }

  private com.balancetrail.entity.ReconciliationItemEntity decided(ProcessedLine line) {
    assertThat(line).isInstanceOf(ProcessedLine.Decided.class);
    return ((ProcessedLine.Decided) line).item();
  }

  private RawGatewayRecord raw(long line, String id, String amount, String date) {
    return new RawGatewayRecord(line, id, "ACC-1", amount, date, null);
  }
}
