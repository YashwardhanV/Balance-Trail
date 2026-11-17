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
    long accountMismatchCount,
    long dateMismatchCount,
    long missingInLedgerCount,
    long missingInGatewayCount,
    long invalidCount,
    long duplicateCount) {

  public static ReconciliationSummaryResponse from(ReconciliationRunEntity run) {
    return new ReconciliationSummaryResponse(
        run.getId(),
        run.getStatus(),
        run.getTotalCount(),
        run.getMatchedCount(),
        run.getAmountMismatchCount(),
        run.getAccountMismatchCount(),
        run.getDateMismatchCount(),
        run.getMissingCount(),
        run.getMissingInGatewayCount(),
        run.getInvalidCount(),
        run.getDuplicateCount());
  }
}
