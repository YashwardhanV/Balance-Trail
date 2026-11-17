package com.balancetrail.dto;

import com.balancetrail.domain.RunStatus;
import com.balancetrail.entity.ReconciliationRunEntity;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code totalCount} is the number of CSV lines. {@code missingCount} means missing in the ledger
 * (kept under its old name so existing clients keep working); {@code missingInGatewayCount} counts
 * ledger rows the file never mentions and is not part of {@code totalCount}.
 */
public record ReconciliationRunResponse(
    UUID id,
    String fileName,
    String fileSha256,
    RunStatus status,
    long totalCount,
    long matchedCount,
    long amountMismatchCount,
    long accountMismatchCount,
    long dateMismatchCount,
    long missingCount,
    long missingInGatewayCount,
    long invalidCount,
    long duplicateCount,
    long suggestedPairCount,
    long ledgerVersion,
    Long gatewayFormatId,
    String failureMessage,
    Instant createdAt,
    Instant startedAt,
    Instant finishedAt) {

  public static ReconciliationRunResponse from(ReconciliationRunEntity run) {
    return new ReconciliationRunResponse(
        run.getId(),
        run.getOriginalFileName(),
        run.getFileSha256(),
        run.getStatus(),
        run.getTotalCount(),
        run.getMatchedCount(),
        run.getAmountMismatchCount(),
        run.getAccountMismatchCount(),
        run.getDateMismatchCount(),
        run.getMissingCount(),
        run.getMissingInGatewayCount(),
        run.getInvalidCount(),
        run.getDuplicateCount(),
        run.getSuggestedPairCount(),
        run.getLedgerVersion(),
        run.getGatewayFormatId(),
        run.getFailureMessage(),
        run.getCreatedAt(),
        run.getStartedAt(),
        run.getFinishedAt());
  }
}
