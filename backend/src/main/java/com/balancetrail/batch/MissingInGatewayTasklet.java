package com.balancetrail.batch;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Second step of the reconciliation job: the other direction.
 *
 * <p>Step 1 asked "is every gateway line in the ledger?". This step asks "is every ledger row in
 * the gateway file?". It only looks at ledger rows dated between the earliest and latest date of
 * the file, otherwise every old ledger row would be reported as missing.
 *
 * <p>Assumption: one gateway file covers all ledger payments in its date range. If a company
 * receives several gateway files for the same days, the ledger would need a gateway column to
 * scope this check (not built here).
 *
 * <p>It is one {@code INSERT ... SELECT}, so the database does the work without loading the ledger
 * into Java. The second {@code NOT EXISTS} also skips rows this step already inserted, so running it
 * twice for the same run inserts nothing new. (Two separate {@code NOT EXISTS} instead of one with
 * {@code OR}: PostgreSQL can turn each into a fast hash anti-join, but not an {@code OR}.)
 */
public class MissingInGatewayTasklet implements Tasklet {
  private static final Logger log = LoggerFactory.getLogger(MissingInGatewayTasklet.class);

  // reconciliation_item.id defaults to nextval('reconciliation_item_seq'), the same sequence
  // Hibernate uses, so these rows never collide with the ones saved by the chunk step.
  private static final String INSERT_LEDGER_ONLY_ROWS =
      """
      WITH file_range AS (
          SELECT MIN(transaction_date) AS first_day, MAX(transaction_date) AS last_day
          FROM reconciliation_item
          WHERE run_id = ? AND transaction_date IS NOT NULL
      )
      INSERT INTO reconciliation_item
          (run_id, line_number, ledger_transaction_id, ledger_account_number,
           ledger_amount, ledger_transaction_date, status, reason, created_at)
      SELECT ?, NULL, l.transaction_ref, l.account_number,
             l.amount, l.transaction_date, 'MISSING_IN_GATEWAY',
             'Ledger transaction dated within this file''s date range is not in the gateway file',
             now()
      FROM ledger_transaction l, file_range r
      WHERE l.transaction_date BETWEEN r.first_day AND r.last_day
        AND NOT EXISTS (
            SELECT 1 FROM reconciliation_item i
            WHERE i.run_id = ? AND i.gateway_transaction_id = l.transaction_ref)
        AND NOT EXISTS (
            SELECT 1 FROM reconciliation_item i
            WHERE i.run_id = ? AND i.ledger_transaction_id = l.transaction_ref)
      """;

  private final JdbcTemplate jdbcTemplate;
  private final UUID runId;

  public MissingInGatewayTasklet(JdbcTemplate jdbcTemplate, UUID runId) {
    this.jdbcTemplate = jdbcTemplate;
    this.runId = runId;
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    int inserted = jdbcTemplate.update(INSERT_LEDGER_ONLY_ROWS, runId, runId, runId, runId);
    contribution.incrementWriteCount(inserted);
    log.info("Run {}: {} ledger transactions are missing from the gateway file", runId, inserted);
    return RepeatStatus.FINISHED;
  }
}
