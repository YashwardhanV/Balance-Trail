# Changelog

## 0.3.0

Two features, kept deliberately small: suggested pairs for ID typos, and gateway formats with an optional AI (Google Gemini) column-mapping suggestion. The scope limits are listed at the end; please keep them.

### Suggested pairs: "probably the same payment"

- A typo in a transaction ID used to show up as two unrelated problems: `MISSING_IN_LEDGER` and `MISSING_IN_GATEWAY`. A new third job step (`PairSuggestionTasklet` + `PairSuggester`) suggests a pair when both rows have the same account and amount, dates within tolerance, and IDs at most 2 characters apart (Levenshtein distance, `MATCH_MAX_ID_DISTANCE`).
- Rows that fit more than one partner are marked ambiguous; nothing is guessed, nothing is confirmed automatically.
- The analyst confirms or rejects (optional note). Who and when is stored; the original results are never changed. Confirming closes the other open suggestions for the same rows. Partial unique indexes make "one confirmed pair per row" a database rule.
- API: `GET /reconciliations/{id}/suggestions`, `POST .../suggestions/{sid}/confirm`, `POST .../suggestions/{sid}/reject`. Runs have `suggestedPairCount`.
- UI: a "Probably the same payment" panel with both rows side by side.

### Gateway formats and AI column mapping

- `POST /gateway-formats/suggest` reads a sample file (header + 20 rows) and proposes which column is the transaction ID, account, amount and date, and the date format. Nothing is saved.
  - Header-name rules first (`HeaderAliasSuggester`). The AI is asked only if the rules can't place all four columns.
  - The AI sees header names and **masked** sample rows only (`ACC-1001` → `AAA-9999`). The answer schema only allows the file's own headers or `NONE`; anything else is dropped by `ColumnMappingValidator`. Any failure falls back to the rules with a warning.
  - The date format is detected locally from real values against a fixed allow-list, never by the AI. Ambiguous dates (`01/07` vs `07/01`) are flagged for the analyst.
- `POST /gateway-formats` saves the reviewed mapping. Formats are immutable; a correction is a new format.
- `POST /reconciliations?formatId=` reads a file through a saved format (`MappedGatewayLineMapper`). Line numbers stay those of the uploaded file. The format is part of the "same upload again?" key.
- Gemini via the REST API (`gemini-3.8-flash` by default, `GEMINI_MODEL`), key from `GEMINI_API_KEY`. No key = AI off, everything else works.
- `src/test/resources/mapping-eval/cases.json`: 12 realistic header sets. Rules are checked against it in every build; `GeminiEvalTest` checks the real Gemini (≥ 90% of fields) only when a key is set.
- UI: "Gateway formats" panel (sample upload, editable mapping, "what an AI would see"), and a File layout picker on upload.

### Other changes

- New demo files: `gateway-settlement-report.csv` (rules map it), `gateway-hard-headers.csv` (needs AI or the analyst), and a typo row (`TXN-1O08`) in `gateway-transactions.csv`.
- Benchmark generator: typo pairs, `--layout messy`, and a fix so no two rows share account and amount.
- Database: Flyway `V4__pair_suggestions_and_gateway_formats.sql`.
- Tests: 82 backend tests (was 45) plus the key-gated Gemini eval, and 9 frontend tests (was 6).

### Deliberately not built (keep it this way)

One-to-many matching (subset-sum), fee netting, currency symbols or thousands separators in amounts, Excel files, auto-applying an AI mapping, learning from corrections, several AI providers. Each is a project of its own and would take this past a focused, explainable codebase.

### Not verified in this release

The real Gemini endpoint was not called while building this (no API key available). The request follows Google's current `generateContent` documentation and is tested against a fake HTTP server, including errors, blocked answers and invalid answers. Run `GeminiEvalTest` with a real key before relying on the AI suggestion.

## 0.2.0

This release closes the gaps listed in the old "Known limitations" and fixes two correctness problems in the matching itself.

### Matching is now correct in both directions

- **Account and date are compared, not just the amount.** Before, a row with the right ID and amount but the wrong account came back `MATCHED`. New statuses: `ACCOUNT_MISMATCH` and `DATE_MISMATCH`. Dates up to 2 days apart still match, because gateways settle a day or two after the ledger records a payment (`app.matching.date-tolerance-days`, env `MATCH_DATE_TOLERANCE_DAYS`). If several fields differ, the most serious wins (amount, account, date) and the reason lists all of them. `TransactionMatcher`.
- **Ledger rows missing from the gateway file are reported.** A second job step finds ledger rows dated inside the file's date range that no line of the file mentions, and records them as `MISSING_IN_GATEWAY`. It is one `INSERT ... SELECT`, so the database does the work. `MissingInGatewayTasklet`.
- Discrepancies now show both sides (gateway account/date and ledger account/date), so the reason for a mismatch is visible in the table.

### Re-uploading a file behaves correctly

- **A failed run no longer blocks its file forever.** The unique constraint on `(owner, file hash)` became a partial unique index on `(owner, file hash, ledger version) WHERE status <> 'FAILED'`. Uploading the same file again after a failure starts a new run.
- **No stale results after the ledger changes.** Each run remembers the ledger version it used. The same file after a ledger import is processed again instead of returning the old run.

### Ledger upload

- `POST /ledger-imports` imports a ledger CSV with a Spring Batch job of the same shape as the reconciliation job and the same validation rules (`FieldRules`, shared by both files). New IDs are inserted, existing IDs updated (upsert). Rejected lines are stored with their reason (`GET /ledger-imports/{id}/errors`).
- A ledger import and a reconciliation never run at the same time (409), so a file is never compared against a half-imported ledger.
- After every import the job runs `ANALYZE ledger_transaction`. Without it, PostgreSQL could keep using a query plan made when the ledger was small, and a 100,000-row run right after a big import took 56 s instead of 14 s.

### Performance

- **One ledger query per chunk instead of one per row.** The processor no longer queries the database; the new `LedgerMatchingItemWriter` loads the ledger rows for all 100 lines of a chunk with one `IN (...)` query, then compares. For 100,000 rows: about 1,000 ledger queries instead of 100,000. With 1 ms of simulated network delay to the database: about 170 s → 23 s. See `docs/BENCHMARK_RESULTS.md`.

### Reliability

- **Runs left unfinished by a restart are marked `FAILED` at startup** instead of staying `RUNNING` forever (same for ledger imports, which would otherwise block every upload). `InterruptedWorkRecovery`.

### Export

- `GET /reconciliations/{id}/discrepancies/export` streams all discrepancies as CSV, 1,000 rows at a time. Values that Excel would run as a formula are made safe. The dashboard has a "Download CSV" button.

### Frontend

- Ledger panel: upload a ledger, see the last 5 imports, open the rejected lines.
- New status badges, a "Missing in file" card, mismatch counts split by amount/account/date, gateway and ledger details per discrepancy, CSV download.
- Tests for the new count and label helpers.

### Database (Flyway `V3__two_way_matching_and_ledger_imports.sql`)

- New item statuses, nullable `line_number` (only for ledger-only rows), `ledger_account_number`, `ledger_transaction_date`.
- New run columns: `account_mismatch_count`, `date_mismatch_count`, `missing_in_gateway_count`, `ledger_version`.
- New tables `ledger_import` and `ledger_import_error`, plus indexes for step 2.

### API changes to note

- `ReconciliationRunResponse` and the summary have new count fields and `ledgerVersion`. `missingCount` keeps its meaning (missing in the ledger).
- `DiscrepancyResponse.lineNumber` can be `null` (ledger-only rows), and it has new ledger-side fields.
- `totalCount` is still the number of CSV lines; `missingInGatewayCount` is separate.

### Tests

45 backend tests (was 18) and 6 frontend tests (was 2). New: matcher rules, chunk writer (one query per chunk, retry safety), ledger import processor, CSV escaping, and integration tests for two-way matching, export, failed-run retry, ledger import + version change, the 409 rules, and restart recovery.
