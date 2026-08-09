package com.balancetrail.service;

import com.balancetrail.domain.ItemStatus;
import com.balancetrail.exception.ResourceNotFoundException;
import com.balancetrail.repository.ReconciliationItemRepository;
import com.balancetrail.repository.ReconciliationRunRepository;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunStateService {
  private final ReconciliationRunRepository runRepository;
  private final ReconciliationItemRepository itemRepository;

  public RunStateService(
      ReconciliationRunRepository runRepository,
      ReconciliationItemRepository itemRepository) {
    this.runRepository = runRepository;
    this.itemRepository = itemRepository;
  }

  @Transactional
  public void markRunning(UUID runId) {
    var run = find(runId);
    run.markRunning();
  }

  @Transactional
  public void summarize(UUID runId) {
    Map<ItemStatus, Long> counts = new EnumMap<>(ItemStatus.class);
    for (Object[] row : itemRepository.countByStatus(runId)) {
      counts.put((ItemStatus) row[0], (Long) row[1]);
    }
    long matched = counts.getOrDefault(ItemStatus.MATCHED, 0L);
    long mismatch = counts.getOrDefault(ItemStatus.AMOUNT_MISMATCH, 0L);
    long missing = counts.getOrDefault(ItemStatus.MISSING_IN_LEDGER, 0L);
    long invalid = counts.getOrDefault(ItemStatus.INVALID, 0L);
    long duplicate = counts.getOrDefault(ItemStatus.DUPLICATE, 0L);
    find(runId).complete(matched + mismatch + missing + invalid + duplicate, matched, mismatch, missing, invalid, duplicate);
  }

  @Transactional
  public void markFailed(UUID runId, String message) {
    find(runId).fail(message);
  }

  private com.balancetrail.entity.ReconciliationRunEntity find(UUID runId) {
    return runRepository
        .findById(runId)
        .orElseThrow(() -> new ResourceNotFoundException("Reconciliation run not found"));
  }
}
