package com.balancetrail.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balancetrail.domain.GatewayTransaction;
import com.balancetrail.domain.ItemStatus;
import com.balancetrail.entity.LedgerTransactionEntity;
import com.balancetrail.entity.ReconciliationItemEntity;
import com.balancetrail.entity.ReconciliationRunEntity;
import com.balancetrail.repository.LedgerTransactionRepository;
import com.balancetrail.service.TransactionMatcher;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;

class LedgerMatchingItemWriterTest {
  private static final LocalDate DAY = LocalDate.of(2026, 8, 1);

  private LedgerTransactionRepository ledgerRepository;
  private ReconciliationRunEntity run;
  private List<ReconciliationItemEntity> saved;
  private LedgerMatchingItemWriter writer;

  @BeforeEach
  void setUp() {
    ledgerRepository = mock(LedgerTransactionRepository.class);
    run = mock(ReconciliationRunEntity.class);
    saved = new ArrayList<>();
    writer =
        new LedgerMatchingItemWriter(
            run, ledgerRepository, new TransactionMatcher(2), chunk -> saved.addAll(chunk.getItems()));
  }

  @Test
  void looksUpTheWholeChunkWithOneQueryAndKeepsFileOrder() throws Exception {
    when(ledgerRepository.findAllById(any()))
        .thenReturn(
            List.of(
                ledger("TX-1", "10.00", "ACC-1"),
                ledger("TX-2", "99.00", "ACC-2"),
                ledger("TX-3", "30.00", "ACC-OTHER")));
    var duplicate =
        ReconciliationItemEntity.result(run, gateway(5, "TX-1", "10.00", "ACC-1"), null, ItemStatus.DUPLICATE, "dup");

    writer.write(
        new Chunk<>(
            awaiting(2, "TX-1", "10.00", "ACC-1"),
            awaiting(3, "TX-2", "20.00", "ACC-2"),
            awaiting(4, "TX-3", "30.00", "ACC-3"),
            new ProcessedLine.Decided(duplicate),
            awaiting(6, "TX-404", "40.00", "ACC-4")));

    verify(ledgerRepository, times(1)).findAllById(Set.of("TX-1", "TX-2", "TX-3", "TX-404"));
    assertThat(saved)
        .extracting(ReconciliationItemEntity::getStatus)
        .containsExactly(
            ItemStatus.MATCHED,
            ItemStatus.AMOUNT_MISMATCH,
            ItemStatus.ACCOUNT_MISMATCH,
            ItemStatus.DUPLICATE,
            ItemStatus.MISSING_IN_LEDGER);
    assertThat(saved.get(2).getLedgerAccountNumber()).isEqualTo("ACC-OTHER");
  }

  @Test
  void chunkWithNothingToLookUpSkipsTheQuery() throws Exception {
    var invalid = ReconciliationItemEntity.invalid(run, 2, "TX-1", "ACC-1", "bad amount");

    writer.write(new Chunk<>(new ProcessedLine.Decided(invalid)));

    verify(ledgerRepository, never()).findAllById(any());
    assertThat(saved).containsExactly(invalid);
  }

  @Test
  void writingTheSameChunkTwiceBuildsFreshResults() throws Exception {
    when(ledgerRepository.findAllById(any())).thenReturn(List.of(ledger("TX-1", "10.00", "ACC-1")));
    var chunk = new Chunk<ProcessedLine>(awaiting(2, "TX-1", "10.00", "ACC-1"));

    writer.write(chunk); // first attempt, imagine it rolled back
    writer.write(chunk); // retry

    assertThat(saved).hasSize(2);
    assertThat(saved.get(0)).isNotSameAs(saved.get(1));
  }

  private ProcessedLine awaiting(long line, String id, String amount, String account) {
    return new ProcessedLine.AwaitingLedger(gateway(line, id, amount, account));
  }

  private GatewayTransaction gateway(long line, String id, String amount, String account) {
    return new GatewayTransaction(line, id, account, new BigDecimal(amount), DAY);
  }

  private LedgerTransactionEntity ledger(String id, String amount, String account) {
    return new LedgerTransactionEntity(id, account, new BigDecimal(amount), DAY, "test");
  }
}
