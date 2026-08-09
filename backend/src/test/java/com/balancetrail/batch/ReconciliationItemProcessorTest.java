package com.balancetrail.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.balancetrail.domain.ItemStatus;
import com.balancetrail.entity.LedgerTransactionEntity;
import com.balancetrail.entity.ReconciliationRunEntity;
import com.balancetrail.exception.DuplicateTransactionException;
import com.balancetrail.exception.InvalidRecordException;
import com.balancetrail.repository.LedgerTransactionRepository;
import com.balancetrail.service.TransactionMatcher;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReconciliationItemProcessorTest {
  private LedgerTransactionRepository ledgerRepository;
  private ReconciliationRunEntity run;

  @BeforeEach
  void setUp() {
    ledgerRepository = mock(LedgerTransactionRepository.class);
    run = mock(ReconciliationRunEntity.class);
  }

  @Test
  void createsAmountMismatchResult() throws Exception {
    var ledger =
        new LedgerTransactionEntity(
            "TX-1", "ACC-1", new BigDecimal("11.00"), LocalDate.of(2026, 8, 1), "test");
    when(ledgerRepository.findById("TX-1")).thenReturn(Optional.of(ledger));
    var processor = processor(List.of());

    var result = processor.process(raw("TX-1", "10.00", "2026-08-01"));

    assertThat(result.getStatus()).isEqualTo(ItemStatus.AMOUNT_MISMATCH);
    assertThat(result.getLedgerAmount()).isEqualByComparingTo("11.00");
  }

  @Test
  void rejectsMalformedAmountsBeforeDuplicateTracking() {
    var processor = processor(List.of());

    assertThatThrownBy(() -> processor.process(raw("TX-1", "10.001", "2026-08-01")))
        .isInstanceOf(InvalidRecordException.class)
        .hasMessageContaining("at most 2 decimals");
  }

  @Test
  void detectsIdsAlreadyProcessedBeforeARestart() {
    var processor = processor(List.of("TX-1"));

    assertThatThrownBy(() -> processor.process(raw("TX-1", "10.00", "2026-08-01")))
        .isInstanceOf(DuplicateTransactionException.class);
  }

  @Test
  void samePhysicalLineCanBeReprocessedAfterAChunkRollback() throws Exception {
    when(ledgerRepository.findById("TX-1")).thenReturn(Optional.empty());
    var processor = processor(List.of());
    var input = raw("TX-1", "10.00", "2026-08-01");

    assertThat(processor.process(input).getStatus()).isEqualTo(ItemStatus.MISSING_IN_LEDGER);
    assertThat(processor.process(input).getStatus()).isEqualTo(ItemStatus.MISSING_IN_LEDGER);
  }

  @Test
  void laterPhysicalLineWithSameIdIsDuplicate() throws Exception {
    when(ledgerRepository.findById("TX-1")).thenReturn(Optional.empty());
    var processor = processor(List.of());
    processor.process(raw("TX-1", "10.00", "2026-08-01"));
    var later = new RawGatewayRecord(3, "TX-1", "ACC-1", "10.00", "2026-08-01", null);

    assertThatThrownBy(() -> processor.process(later))
        .isInstanceOf(DuplicateTransactionException.class);
  }

  private ReconciliationItemProcessor processor(List<String> processed) {
    return new ReconciliationItemProcessor(
        run, new DuplicateDetector(processed), ledgerRepository, new TransactionMatcher());
  }

  private RawGatewayRecord raw(String id, String amount, String date) {
    return new RawGatewayRecord(2, id, "ACC-1", amount, date, null);
  }
}
