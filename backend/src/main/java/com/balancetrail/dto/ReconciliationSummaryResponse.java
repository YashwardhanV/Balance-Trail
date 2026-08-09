package com.balancetrail.dto;

import com.balancetrail.domain.RunStatus;
import com.balancetrail.entity.ReconciliationRunEntity;
import java.util.UUID;

public record ReconciliationSummaryResponse(
    UUID reconciliationId,
    RunStatus status,
    long totalCount,
    long matchedCount,
    long amountMismatchCount,
    long missingInLedgerCount,
    long invalidCount,
    long duplicateCount,
    long skippedCount) {

  public static ReconciliationSummaryResponse from(ReconciliationRunEntity run) {
    return new ReconciliationSummaryResponse(
        run.getId(),
        run.getStatus(),
        run.getTotalCount(),
        run.getMatchedCount(),
        run.getAmountMismatchCount(),
        run.getMissingCount(),
        run.getInvalidCount(),
        run.getDuplicateCount(),
        run.getInvalidCount() + run.getDuplicateCount());
  }
}
