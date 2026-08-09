package com.balancetrail.service;

import com.balancetrail.batch.RawGatewayRecord;
import com.balancetrail.domain.ItemStatus;
import com.balancetrail.entity.ReconciliationItemEntity;
import com.balancetrail.repository.ReconciliationItemRepository;
import com.balancetrail.repository.ReconciliationRunRepository;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkippedItemRecorder {
  private final ReconciliationRunRepository runRepository;
  private final ReconciliationItemRepository itemRepository;

  public SkippedItemRecorder(
      ReconciliationRunRepository runRepository,
      ReconciliationItemRepository itemRepository) {
    this.runRepository = runRepository;
    this.itemRepository = itemRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(UUID runId, RawGatewayRecord raw, ItemStatus status, String reason) {
    if (itemRepository.existsByRunIdAndLineNumber(runId, raw.lineNumber())) {
      return;
    }
    var run = runRepository.getReferenceById(runId);
    var item =
        ReconciliationItemEntity.skipped(
            run,
            raw.lineNumber(),
            raw.transactionId(),
            raw.accountNumber(),
            status,
            reason.substring(0, Math.min(500, reason.length())));
    try {
      itemRepository.saveAndFlush(item);
    } catch (DataIntegrityViolationException exception) {
      if (!itemRepository.existsByRunIdAndLineNumber(runId, raw.lineNumber())) {
        throw exception;
      }
    }
  }
}
