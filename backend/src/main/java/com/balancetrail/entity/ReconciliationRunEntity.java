package com.balancetrail.entity;

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
import jakarta.persistence.Version;
import java.time.Instant;
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

  @Column(name = "failure_message", length = 1000)
  private String failureMessage;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected ReconciliationRunEntity() {}

  public static ReconciliationRunEntity pending(
      AppUserEntity owner, String originalFileName, String fileSha256, String storedFilePath) {
    var run = new ReconciliationRunEntity();
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

  public void complete(
      long total,
      long matched,
      long amountMismatch,
      long missing,
      long invalid,
      long duplicate) {
    totalCount = total;
    matchedCount = matched;
    amountMismatchCount = amountMismatch;
    missingCount = missing;
    invalidCount = invalid;
    duplicateCount = duplicate;
    status = invalid + duplicate > 0 ? RunStatus.COMPLETED_WITH_SKIPS : RunStatus.COMPLETED;
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
