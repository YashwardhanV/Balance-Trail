package com.balancetrail.service;

import com.balancetrail.entity.LedgerImportEntity;
import com.balancetrail.exception.ResourceNotFoundException;
import com.balancetrail.repository.LedgerImportRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the ledger_import row in step with its batch job. */
@Service
public class LedgerImportStateService {
  private final LedgerImportRepository importRepository;
  private final JdbcTemplate jdbcTemplate;

  public LedgerImportStateService(LedgerImportRepository importRepository, JdbcTemplate jdbcTemplate) {
    this.importRepository = importRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public void markRunning(long importId) {
    find(importId).markRunning();
  }

  /** {@code linesWritten} is every line the job saved, valid or rejected. */
  @Transactional
  public void complete(long importId, long linesWritten) {
    Long invalid =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ledger_import_error WHERE import_id = ?", Long.class, importId);
    find(importId).complete(linesWritten, invalid == null ? 0 : invalid);
  }

  /**
   * Refreshes PostgreSQL's statistics for the ledger table after an import changed it.
   *
   * <p>Why: reconciliation looks up a whole chunk with {@code WHERE transaction_ref IN (...)}.
   * PostgreSQL caches the plan for that query per connection. If the plan was made while the
   * ledger was small, it may scan the whole table, and it keeps doing that after an import has
   * added 100,000 rows, until auto-analyze happens to run. Measured: a 100,000-row reconciliation
   * took 56 s right after a big ledger load and 14 s with this ANALYZE. ANALYZE also makes
   * PostgreSQL throw away the old cached plans.
   */
  public void refreshLedgerStatistics() {
    jdbcTemplate.execute("ANALYZE ledger_transaction");
  }

  @Transactional
  public void markFailed(long importId, String message) {
    find(importId).fail(message);
  }

  private LedgerImportEntity find(long importId) {
    return importRepository
        .findById(importId)
        .orElseThrow(() -> new ResourceNotFoundException("Ledger import not found"));
  }
}
