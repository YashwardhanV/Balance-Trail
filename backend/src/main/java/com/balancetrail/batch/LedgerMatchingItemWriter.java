package com.balancetrail.batch;

import com.balancetrail.entity.LedgerTransactionEntity;
import com.balancetrail.entity.ReconciliationItemEntity;
import com.balancetrail.entity.ReconciliationRunEntity;
import com.balancetrail.repository.LedgerTransactionRepository;
import com.balancetrail.service.TransactionMatcher;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

/**
 * Finishes a chunk: loads the ledger rows for every valid line with ONE query, compares each line,
 * then saves all results in the chunk's transaction.
 *
 * <p>Before this, the processor ran one {@code SELECT} per CSV line (100 queries per chunk). Now a
 * chunk of 100 lines costs one {@code SELECT ... WHERE transaction_ref IN (...)}.
 *
 * <p>If the chunk is retried after a temporary database error, this method simply runs again and
 * builds fresh result objects, so a retry cannot save a result twice.
 */
public class LedgerMatchingItemWriter implements ItemWriter<ProcessedLine> {
  private final ReconciliationRunEntity run;
  private final LedgerTransactionRepository ledgerRepository;
  private final TransactionMatcher matcher;
  private final ItemWriter<ReconciliationItemEntity> delegate;

  public LedgerMatchingItemWriter(
      ReconciliationRunEntity run,
      LedgerTransactionRepository ledgerRepository,
      TransactionMatcher matcher,
      ItemWriter<ReconciliationItemEntity> delegate) {
    this.run = run;
    this.ledgerRepository = ledgerRepository;
    this.matcher = matcher;
    this.delegate = delegate;
  }

  @Override
  public void write(Chunk<? extends ProcessedLine> chunk) throws Exception {
    Map<String, LedgerTransactionEntity> ledgerById = loadLedger(chunk);

    List<ReconciliationItemEntity> results = new ArrayList<>(chunk.size());
    for (ProcessedLine line : chunk) {
      switch (line) {
        case ProcessedLine.Decided decided -> results.add(decided.item());
        case ProcessedLine.AwaitingLedger awaiting -> {
          var gateway = awaiting.gateway();
          var ledger = Optional.ofNullable(ledgerById.get(gateway.transactionId()));
          var decision = matcher.match(gateway, ledger);
          results.add(
              ReconciliationItemEntity.result(
                  run, gateway, ledger.orElse(null), decision.status(), decision.reason()));
        }
      }
    }
    delegate.write(new Chunk<>(results));
  }

  private Map<String, LedgerTransactionEntity> loadLedger(Chunk<? extends ProcessedLine> chunk) {
    Set<String> ids = new LinkedHashSet<>();
    for (ProcessedLine line : chunk) {
      if (line instanceof ProcessedLine.AwaitingLedger awaiting) {
        ids.add(awaiting.gateway().transactionId());
      }
    }
    if (ids.isEmpty()) {
      return Map.of();
    }
    return ledgerRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(LedgerTransactionEntity::getTransactionRef, Function.identity()));
  }
}
