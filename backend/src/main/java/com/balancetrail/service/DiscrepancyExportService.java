package com.balancetrail.service;

import java.io.PrintWriter;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Streams every discrepancy of a run as CSV. Rows go from the database straight to the HTTP
 * response 1,000 at a time, so a run with 100,000 discrepancies never sits in memory at once.
 */
@Service
public class DiscrepancyExportService {
  static final String HEADER =
      "line_number,status,gateway_transaction_id,gateway_account_number,gateway_amount,gateway_date,"
          + "ledger_transaction_id,ledger_account_number,ledger_amount,ledger_date,reason";

  private static final String SELECT_DISCREPANCIES =
      """
      SELECT line_number, status, gateway_transaction_id, account_number, gateway_amount,
             transaction_date, ledger_transaction_id, ledger_account_number, ledger_amount,
             ledger_transaction_date, reason
      FROM reconciliation_item
      WHERE run_id = ? AND status <> 'MATCHED'
      ORDER BY line_number NULLS LAST, ledger_transaction_id
      """;

  private final ReconciliationService reconciliationService;
  private final JdbcTemplate streamingJdbc;

  public DiscrepancyExportService(ReconciliationService reconciliationService, DataSource dataSource) {
    this.reconciliationService = reconciliationService;
    this.streamingJdbc = new JdbcTemplate(dataSource);
    // PostgreSQL only streams with a fetch size inside a transaction (hence @Transactional below).
    this.streamingJdbc.setFetchSize(1000);
  }

  /** Checks ownership first (404 if not yours), then writes the CSV. */
  public void checkAccess(UUID runId, String username) {
    reconciliationService.ownedRun(runId, username);
  }

  @Transactional(readOnly = true)
  public void write(UUID runId, String username, PrintWriter out) {
    reconciliationService.ownedRun(runId, username);
    out.print(HEADER);
    out.print("\r\n");
    streamingJdbc.query(
        SELECT_DISCREPANCIES,
        resultSet -> {
          for (int column = 1; column <= 11; column++) {
            if (column > 1) {
              out.print(',');
            }
            out.print(CsvText.cell(resultSet.getObject(column)));
          }
          out.print("\r\n");
        },
        runId);
    out.flush();
  }
}
