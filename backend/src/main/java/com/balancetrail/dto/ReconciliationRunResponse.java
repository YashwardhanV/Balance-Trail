package com.balancetrail.dto;

import com.balancetrail.domain.RunStatus;
import com.balancetrail.entity.ReconciliationRunEntity;
import java.time.Instant;
import java.util.UUID;

public record ReconciliationRunResponse(
    UUID id,
    String fileName,
    String fileSha256,
    RunStatus status,
    long totalCount,
    long matchedCount,
    long amountMismatchCount,
    long missingCount,
    long invalidCount,
    long duplicateCount,
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
        run.getMissingCount(),
        run.getInvalidCount(),
        run.getDuplicateCount(),
        run.getFailureMessage(),
        run.getCreatedAt(),
        run.getStartedAt(),
        run.getFinishedAt());
  }
}
