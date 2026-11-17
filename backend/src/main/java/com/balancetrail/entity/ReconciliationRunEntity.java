package com.balancetrail.entity;

import com.balancetrail.domain.ItemStatus;
import com.balancetrail.domain.RunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_run")
public class ReconciliationRunEntity {

  @Id @GeneratedValue private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "owner_id", nullable = false)
  private AppUserEntity owner;

  @Column(name = "original_file_name", nullable = false, length = 255)
  private String originalFileName;

  @Column(name = "file_sha256", nullable = false, length = 64)
  private String fileSha256;

  @Column(name = "stored_file_path", nullable = false, length = 700)
  private String storedFilePath;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private RunStatus status;

  @Column(name = "total_count", nullable = false)
  private long totalCount;

  @Column(name = "matched_count", nullable = false)
  private long matchedCount;

  @Column(name = "amount_mismatch_count", nullable = false)
  private long amountMismatchCount;

  @Column(name = "missing_count", nullable = false)
  private long missingCount;

  @Column(name = "invalid_count", nullable = false)
  private long invalidCount;

  @Column(name = "duplicate_count", nullable = false)
  private long duplicateCount;

  @Column(name = "account_mismatch_count", nullable = false)
  private long accountMismatchCount;

  @Column(name = "date_mismatch_count", nullable = false)
  private long dateMismatchCount;

  @Column(name = "missing_in_gateway_count", nullable = false)
  private long missingInGatewayCount;

  @Column(name = "suggested_pair_count", nullable = false)
  private long suggestedPairCount;

  /** How the file was read: null = the standard 4-column layout, otherwise a saved gateway format. */
  @Column(name = "gateway_format_id")
  private Long gatewayFormatId;

  /** Which ledger import this run was compared against (0 = the seeded demo ledger). */
  @Column(name = "ledger_version", nullable = false)
  private long ledgerVersion;

  @Column(name = "failure_message", length = 1000)
  private String failureMessage;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  protected ReconciliationRunEntity() {}

  public static ReconciliationRunEntity pending(
      AppUserEntity owner,
      String originalFileName,
      String fileSha256,
      String storedFilePath,
      long ledgerVersion,
      Long gatewayFormatId) {
    var run = new ReconciliationRunEntity();
    run.ledgerVersion = ledgerVersion;
    run.gatewayFormatId = gatewayFormatId;
    run.owner = owner;
    run.originalFileName = originalFileName;
    run.fileSha256 = fileSha256;
    run.storedFilePath = storedFilePath;
    run.status = RunStatus.PENDING;
    run.createdAt = Instant.now();
    return run;
  }

  public void markRunning() {
    status = RunStatus.RUNNING;
    startedAt = Instant.now();
    failureMessage = null;
  }

  /**
   * Stores the final counts. {@code totalCount} is the number of CSV lines, so ledger-only
   * MISSING_IN_GATEWAY rows are counted separately and not included in it.
   */
  public void complete(Map<ItemStatus, Long> counts, long suggestedPairs) {
    suggestedPairCount = suggestedPairs;
    matchedCount = counts.getOrDefault(ItemStatus.MATCHED, 0L);
    amountMismatchCount = counts.getOrDefault(ItemStatus.AMOUNT_MISMATCH, 0L);
    accountMismatchCount = counts.getOrDefault(ItemStatus.ACCOUNT_MISMATCH, 0L);
    dateMismatchCount = counts.getOrDefault(ItemStatus.DATE_MISMATCH, 0L);
    missingCount = counts.getOrDefault(ItemStatus.MISSING_IN_LEDGER, 0L);
    missingInGatewayCount = counts.getOrDefault(ItemStatus.MISSING_IN_GATEWAY, 0L);
    invalidCount = counts.getOrDefault(ItemStatus.INVALID, 0L);
    duplicateCount = counts.getOrDefault(ItemStatus.DUPLICATE, 0L);
    totalCount =
        matchedCount
            + amountMismatchCount
            + accountMismatchCount
            + dateMismatchCount
            + missingCount
            + invalidCount
            + duplicateCount;
    status = RunStatus.COMPLETED;
    finishedAt = Instant.now();
  }

  public void fail(String message) {
    status = RunStatus.FAILED;
    failureMessage = message == null ? "Batch execution failed" : message.substring(0, Math.min(1000, message.length()));
    finishedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public AppUserEntity getOwner() {
    return owner;
  }

  public String getOriginalFileName() {
    return originalFileName;
  }

  public String getFileSha256() {
    return fileSha256;
  }

  public String getStoredFilePath() {
    return storedFilePath;
  }

  public RunStatus getStatus() {
    return status;
  }

  public long getTotalCount() {
    return totalCount;
  }

  public long getMatchedCount() {
    return matchedCount;
  }

  public long getAmountMismatchCount() {
    return amountMismatchCount;
  }

  public long getMissingCount() {
    return missingCount;
  }

  public long getInvalidCount() {
    return invalidCount;
  }

  public long getDuplicateCount() {
    return duplicateCount;
  }

  public long getAccountMismatchCount() {
    return accountMismatchCount;
  }

  public long getDateMismatchCount() {
    return dateMismatchCount;
  }

  public long getMissingInGatewayCount() {
    return missingInGatewayCount;
  }

  public long getSuggestedPairCount() {
    return suggestedPairCount;
  }

  public Long getGatewayFormatId() {
    return gatewayFormatId;
  }

  public long getLedgerVersion() {
    return ledgerVersion;
  }

  public String getFailureMessage() {
    return failureMessage;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }
}
