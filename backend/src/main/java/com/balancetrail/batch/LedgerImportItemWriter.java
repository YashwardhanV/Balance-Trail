package com.balancetrail.batch;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Saves one chunk of a ledger file with two JDBC batches: valid rows are upserted into
 * ledger_transaction, rejected rows go to ledger_import_error.
 *
 * <p>Upsert: a new transaction ID is inserted; an existing one is updated, so uploading a corrected
 * ledger fixes the old values. Both statements are safe to repeat, which matters because a chunk
 * is written again if a temporary database error made the first attempt roll back.
 */
public class LedgerImportItemWriter implements ItemWriter<LedgerLine> {
  private static final String UPSERT_LEDGER_ROW =
      """
      INSERT INTO ledger_transaction
          (transaction_ref, account_number, amount, transaction_date, description)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT (transaction_ref) DO UPDATE SET
          account_number = EXCLUDED.account_number,
          amount = EXCLUDED.amount,
          transaction_date = EXCLUDED.transaction_date,
          description = EXCLUDED.description
      """;

  private static final String INSERT_ERROR =
      """
      INSERT INTO ledger_import_error (import_id, line_number, transaction_id, reason)
      VALUES (?, ?, ?, ?)
      ON CONFLICT (import_id, line_number) DO NOTHING
      """;

  private final JdbcTemplate jdbcTemplate;
  private final long importId;

  public LedgerImportItemWriter(JdbcTemplate jdbcTemplate, long importId) {
    this.jdbcTemplate = jdbcTemplate;
    this.importId = importId;
  }

  @Override
  public void write(Chunk<? extends LedgerLine> chunk) {
    List<Object[]> upserts = new ArrayList<>();
    List<Object[]> errors = new ArrayList<>();
    for (LedgerLine line : chunk) {
      switch (line) {
        case LedgerLine.Valid valid ->
            upserts.add(
                new Object[] {
                  valid.transactionId(),
                  valid.accountNumber(),
                  valid.amount(),
                  Date.valueOf(valid.transactionDate()),
                  valid.description()
                });
        case LedgerLine.Invalid invalid ->
            errors.add(
                new Object[] {importId, invalid.lineNumber(), invalid.transactionId(), invalid.reason()});
      }
    }
    if (!upserts.isEmpty()) {
      jdbcTemplate.batchUpdate(UPSERT_LEDGER_ROW, upserts);
    }
    if (!errors.isEmpty()) {
      jdbcTemplate.batchUpdate(INSERT_ERROR, errors);
    }
  }
}
