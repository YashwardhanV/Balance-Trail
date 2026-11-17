package com.balancetrail.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs once at startup. A run or ledger import that was PENDING or RUNNING when the server stopped
 * will never finish, because its job lived in this process's memory. Without this, it would show
 * "RUNNING" forever and (for ledger imports) block new uploads.
 *
 * <p>{@link SmartInitializingSingleton} runs after Flyway has migrated the database but before the
 * web server accepts requests, so it cannot mark a brand-new upload as failed by mistake.
 *
 * <p>Assumes one backend instance. With several instances, one instance restarting would fail the
 * other instances' running jobs; that would need a heartbeat column per job.
 */
@Component
public class InterruptedWorkRecovery implements SmartInitializingSingleton {
  private static final Logger log = LoggerFactory.getLogger(InterruptedWorkRecovery.class);
  static final String MESSAGE = "Server stopped before this job finished. Upload the file again to retry.";

  private final JdbcTemplate jdbcTemplate;

  public InterruptedWorkRecovery(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void afterSingletonsInstantiated() {
    recover();
  }

  /** Marks unfinished runs and ledger imports as FAILED. Returns how many rows were changed. */
  public int recover() {
    int runs = markFailed("reconciliation_run");
    int imports = markFailed("ledger_import");
    if (runs + imports > 0) {
      log.warn("Marked {} reconciliation run(s) and {} ledger import(s) as FAILED after a restart", runs, imports);
    }
    return runs + imports;
  }

  private int markFailed(String table) {
    return jdbcTemplate.update(
        "UPDATE " + table
            + " SET status = 'FAILED', failure_message = ?, finished_at = now()"
            + " WHERE status IN ('PENDING', 'RUNNING')",
        MESSAGE);
  }
}
