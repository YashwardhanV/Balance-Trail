package com.balancetrail.batch;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Third step of the reconciliation job: after both directions are known, suggest pairs between
 * MISSING_IN_LEDGER and MISSING_IN_GATEWAY rows of this run (rules in {@link PairSuggester}).
 *
 * <p>Only the leftover rows are loaded, which is a small share of a file. Saving uses
 * {@code ON CONFLICT DO NOTHING}, so running the step twice does not create duplicates.
 */
public class PairSuggestionTasklet implements Tasklet {
  private static final Logger log = LoggerFactory.getLogger(PairSuggestionTasklet.class);

  private static final String SELECT_MISSING_IN_LEDGER =
      """
      SELECT id, gateway_transaction_id, account_number, gateway_amount, transaction_date
      FROM reconciliation_item WHERE run_id = ? AND status = 'MISSING_IN_LEDGER'
      """;
  private static final String SELECT_MISSING_IN_GATEWAY =
      """
      SELECT id, ledger_transaction_id, ledger_account_number, ledger_amount, ledger_transaction_date
      FROM reconciliation_item WHERE run_id = ? AND status = 'MISSING_IN_GATEWAY'
      """;
  private static final String INSERT_SUGGESTION =
      """
      INSERT INTO match_suggestion
          (run_id, gateway_item_id, ledger_item_id, id_distance, ambiguous, status, created_at)
      VALUES (?, ?, ?, ?, ?, 'PENDING', ?)
      ON CONFLICT (gateway_item_id, ledger_item_id) DO NOTHING
      """;

  private final JdbcTemplate jdbcTemplate;
  private final PairSuggester suggester;
  private final UUID runId;

  public PairSuggestionTasklet(JdbcTemplate jdbcTemplate, PairSuggester suggester, UUID runId) {
    this.jdbcTemplate = jdbcTemplate;
    this.suggester = suggester;
    this.runId = runId;
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    List<PairSuggester.Row> missingInLedger = load(SELECT_MISSING_IN_LEDGER);
    List<PairSuggester.Row> missingInGateway = load(SELECT_MISSING_IN_GATEWAY);
    if (missingInLedger.isEmpty() || missingInGateway.isEmpty()) {
      return RepeatStatus.FINISHED;
    }

    var suggestions = suggester.suggest(missingInLedger, missingInGateway);
    Timestamp now = Timestamp.from(Instant.now());
    List<Object[]> rows = new ArrayList<>(suggestions.size());
    for (var suggestion : suggestions) {
      rows.add(
          new Object[] {
            runId, suggestion.gatewayItemId(), suggestion.ledgerItemId(),
            suggestion.idDistance(), suggestion.ambiguous(), now
          });
    }
    if (!rows.isEmpty()) {
      jdbcTemplate.batchUpdate(INSERT_SUGGESTION, rows);
    }
    contribution.incrementWriteCount(rows.size());
    log.info(
        "Run {}: {} pair suggestions from {} missing-in-ledger and {} missing-in-gateway rows",
        runId, rows.size(), missingInLedger.size(), missingInGateway.size());
    return RepeatStatus.FINISHED;
  }

  private List<PairSuggester.Row> load(String sql) {
    return jdbcTemplate.query(
        sql,
        (resultSet, rowNumber) ->
            new PairSuggester.Row(
                resultSet.getLong(1),
                resultSet.getString(2),
                resultSet.getString(3),
                resultSet.getBigDecimal(4),
                resultSet.getDate(5).toLocalDate()),
        runId);
  }
}
