package com.balancetrail.dto;

import com.balancetrail.domain.RunStatus;
import com.balancetrail.entity.LedgerImportEntity;
import java.time.Instant;

/** {@code id} is also the ledger version that reconciliation runs refer to. */
public record LedgerImportResponse(
    long id,
    String fileName,
    String uploadedBy,
    RunStatus status,
    long totalCount,
    long importedCount,
    long invalidCount,
    String failureMessage,
    Instant createdAt,
    Instant startedAt,
    Instant finishedAt) {

  public static LedgerImportResponse from(LedgerImportEntity ledgerImport) {
    return new LedgerImportResponse(
        ledgerImport.getId(),
        ledgerImport.getOriginalFileName(),
        ledgerImport.getUploadedBy().getUsername(),
        ledgerImport.getStatus(),
        ledgerImport.getTotalCount(),
        ledgerImport.getImportedCount(),
        ledgerImport.getInvalidCount(),
        ledgerImport.getFailureMessage(),
        ledgerImport.getCreatedAt(),
        ledgerImport.getStartedAt(),
        ledgerImport.getFinishedAt());
  }
}
