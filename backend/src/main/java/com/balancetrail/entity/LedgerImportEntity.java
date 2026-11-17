package com.balancetrail.entity;

import com.balancetrail.domain.RunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One uploaded ledger file. Its id doubles as the "ledger version": the highest id is the current
 * version, and every reconciliation run remembers which version it was compared against.
 */
@Entity
@Table(name = "ledger_import")
public class LedgerImportEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ledger_import_seq")
  @SequenceGenerator(name = "ledger_import_seq", sequenceName = "ledger_import_seq", allocationSize = 1)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "uploaded_by", nullable = false)
  private AppUserEntity uploadedBy;

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

  @Column(name = "imported_count", nullable = false)
  private long importedCount;

  @Column(name = "invalid_count", nullable = false)
  private long invalidCount;

  @Column(name = "failure_message", length = 1000)
  private String failureMessage;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  protected LedgerImportEntity() {}

  public static LedgerImportEntity pending(
      AppUserEntity uploadedBy, String originalFileName, String fileSha256, String storedFilePath) {
    var ledgerImport = new LedgerImportEntity();
    ledgerImport.uploadedBy = uploadedBy;
    ledgerImport.originalFileName = originalFileName;
    ledgerImport.fileSha256 = fileSha256;
    ledgerImport.storedFilePath = storedFilePath;
    ledgerImport.status = RunStatus.PENDING;
    ledgerImport.createdAt = Instant.now();
    return ledgerImport;
  }

  public void markRunning() {
    status = RunStatus.RUNNING;
    startedAt = Instant.now();
    failureMessage = null;
  }

  public void complete(long total, long invalid) {
    totalCount = total;
    invalidCount = invalid;
    importedCount = total - invalid;
    status = RunStatus.COMPLETED;
    finishedAt = Instant.now();
  }

  public void fail(String message) {
    status = RunStatus.FAILED;
    failureMessage =
        message == null ? "Ledger import failed" : message.substring(0, Math.min(1000, message.length()));
    finishedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public AppUserEntity getUploadedBy() {
    return uploadedBy;
  }

  public String getOriginalFileName() {
    return originalFileName;
  }

  public RunStatus getStatus() {
    return status;
  }

  public long getTotalCount() {
    return totalCount;
  }

  public long getImportedCount() {
    return importedCount;
  }

  public long getInvalidCount() {
    return invalidCount;
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
