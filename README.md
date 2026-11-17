# BalanceTrail

BalanceTrail checks a payment gateway's settlement file against a company's internal ledger and shows every transaction that doesn't line up, in both directions: gateway rows the ledger doesn't know about, and ledger rows the gateway file never mentions.

An analyst keeps the ledger current by uploading a ledger CSV, then uploads a gateway CSV. A Spring Batch job reads it in chunks of 100 rows, compares each row's amount, account and date with the ledger, and records one result per line. A second step finds ledger rows in the file's date range that the file left out, and a third step suggests pairs that are probably the same payment with a typo in the ID. The dashboard shows the totals, the suggested pairs to confirm or reject, a paginated list of discrepancies, and a CSV download.

Gateway files that don't use the standard 4 columns (other column names, extra columns, dates like `09/07/2026`) are read through a saved **gateway format**. The app suggests the mapping from one sample file: header-name rules first, and Google Gemini only for the columns the rules can't place. An analyst always reviews it before it's saved.

Built by **Yashwardhan Verma** · [GitHub](https://github.com/YashwardhanV) · [LinkedIn](https://www.linkedin.com/in/yashwardhanv)

**Stack:** Java 21, Spring Boot 3.5, Spring Batch, Spring Data JPA, Spring Security, PostgreSQL 17, Flyway, React 19, TypeScript, Tailwind CSS, Docker Compose, GitHub Actions.

## Features

- Upload a gateway CSV and follow the job while it runs (the UI polls every 2 seconds)
- Every line gets exactly one outcome: `MATCHED`, `AMOUNT_MISMATCH`, `ACCOUNT_MISMATCH`, `DATE_MISMATCH`, `MISSING_IN_LEDGER`, `INVALID` or `DUPLICATE`
- Dates up to 2 days apart still match (settlement delay; configurable with `MATCH_DATE_TOLERANCE_DAYS`)
- Ledger rows dated inside the file's date range but missing from the file are reported as `MISSING_IN_GATEWAY`
- Upload the ledger as a CSV too: new IDs are added, existing IDs are updated, bad lines are listed with the reason
- Uploading the same file twice returns the original run, unless that run failed or the ledger has changed since (SHA-256 of the file + ledger version)
- Ledger rows are fetched once per chunk of 100, not once per row
- A run left half-finished by a server restart is marked `FAILED` at startup instead of staying `RUNNING` forever
- Download all discrepancies of a run as CSV (streamed, safe to open in Excel)
- **Suggested pairs:** a gateway line missing from the ledger and a ledger row missing from the file, with the same account, amount and date (within tolerance) and IDs at most 2 characters apart, are shown as "probably the same payment". The analyst confirms or rejects; the decision is recorded and the original results are never changed. Rows that fit more than one partner are flagged, never guessed.
- **Gateway formats:** map a messy export once, then read every file from that gateway with it. Rules first, AI (Gemini) only for what the rules can't place, analyst confirms. Only header names and masked values (`ACC-1001` → `AAA-9999`) are ever sent to the AI, and the date format is worked out locally.
- Retries a chunk up to 3 times if the database has a temporary error
- Login with BCrypt-hashed passwords; each analyst only sees their own runs
- Paginated run history and discrepancy APIs, documented with Swagger UI
- Integration tests against a real PostgreSQL container (Testcontainers)

## How it works

```text
Browser (React)
   │  POST /reconciliations  (CSV file)
   ▼
ReconciliationController ─► ReconciliationService
                               1. store file + compute SHA-256
                               2. ledger import running? → 409, try later
                               3. same file + same ledger, not failed? → return that run (200)
                               4. save run as PENDING (with the current ledger version)
                               5. start job on a background thread → return 202
                                          │
                                          ▼
                     Spring Batch job
                     step 1 (chunks of 100 rows)
                       reader:    CSV line → RawGatewayRecord
                       processor: validate → duplicate?
                       writer:    ONE ledger query for the chunk → compare amount,
                                  account, date → save 100 results in one transaction
                     step 2: one INSERT ... SELECT adds MISSING_IN_GATEWAY rows
                     step 3: suggest typo pairs between the leftovers of steps 1 and 2
                                          │
                     job listener: count results by status → run COMPLETED / FAILED
```

The browser polls `GET /reconciliations` until the run is `COMPLETED` or `FAILED`.

More detail: [architecture](docs/ARCHITECTURE.md) · [database](docs/ER_DIAGRAM.md) · [API](docs/API.md)

## Run it

You need Docker Desktop (or Docker Engine with Compose).

```bash
cp .env.example .env
docker compose up --build
```

Then open <http://localhost:3000> and sign in with `analyst` / `change-me-now`.

Upload `backend/src/main/resources/demo/gateway-transactions.csv` to see all eight outcomes against the seeded demo ledger, plus one suggested pair (`TXN-1O08` with a letter O is probably ledger row `TXN-1008`). Then import `backend/src/main/resources/demo/ledger-update.csv` as a ledger (one line is rejected on purpose) and upload the same gateway file again: because the ledger changed, it runs again instead of returning the old result, and TXN-1009 and TXN-1002 now match.

| URL | What |
|---|---|
| <http://localhost:3000> | Dashboard |
| <http://localhost:8080/swagger-ui.html> | API docs |
| <http://localhost:8080/actuator/health> | Health check |

Stop with `docker compose down`. Add `-v` to also delete the database and uploaded files (needed once if you ran an older version, because the schema changed).

### Without Docker for the app

```bash
docker compose up -d db                      # just PostgreSQL
cd backend && ./mvnw spring-boot:run         # Java 21
cd frontend && npm ci && npm run dev         # Node 24, opens on :5173
```

To try gateway formats, click **Add a format** and choose `demo/gateway-settlement-report.csv`: header rules map all four columns and detect `dd/MM/yyyy`. Save it, pick it under **File layout**, and upload the same file. `demo/gateway-hard-headers.csv` (`Reference`, `A/C`, `Gross Amt`, ...) shows what happens when rules can't place the columns: with `GEMINI_API_KEY` set, Gemini suggests them; without it, you pick them yourself.

### Turning on AI suggestions (optional)

Put a Gemini API key in `.env` as `GEMINI_API_KEY=...` and restart. Without a key everything works the same, minus the AI suggestion. The model is set with `GEMINI_MODEL` (default `gemini-3.8-flash`), because Google renames models often. To check the AI's accuracy after changing the model or prompt:

```bash
cd backend && GEMINI_API_KEY=... ./mvnw test -Dtest=GeminiEvalTest
```

## CSV formats

Gateway file:

```csv
transaction_id,account_number,amount,transaction_date
TXN-1001,ACC-1001,1250.00,2026-07-01
```

Ledger file (same columns plus an optional description, which may be empty):

```csv
transaction_id,account_number,amount,transaction_date,description
TXN-1001,ACC-1001,1250.00,2026-07-01,Card settlement
```

- `transaction_id`: required, up to 64 characters
- `account_number`: required, up to 32 characters
- `amount`: greater than zero, at most 2 decimal places
- `transaction_date`: `yyyy-MM-dd`
- `description` (ledger only): up to 160 characters

## Matching rules

| Condition | Status |
|---|---|
| Row is malformed or a field is invalid | `INVALID` |
| Transaction ID already appeared on an earlier line of this file | `DUPLICATE` |
| No ledger row with this transaction ID | `MISSING_IN_LEDGER` |
| Amounts differ | `AMOUNT_MISMATCH` |
| Amounts equal, account numbers differ | `ACCOUNT_MISMATCH` |
| Amount and account equal, dates more than 2 days apart | `DATE_MISMATCH` |
| All three agree (dates within 2 days) | `MATCHED` |
| Ledger row dated inside the file's date range, not in the file | `MISSING_IN_GATEWAY` |

After matching, every `MISSING_IN_LEDGER` row is compared with every `MISSING_IN_GATEWAY` row of the same account and amount. If the dates are within tolerance and the IDs are at most 2 characters apart (`MATCH_MAX_ID_DISTANCE`), the pair is suggested.

When several fields differ, the most serious wins (amount, then account, then date) and the reason lists all of them.

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/reconciliations` | Upload a gateway CSV (202 new run, 200 same file + same ledger again, 409 while a ledger import runs) |
| `GET` | `/reconciliations?page=0&size=20` | My runs, newest first |
| `GET` | `/reconciliations/{id}` | One run with its counts |
| `GET` | `/reconciliations/{id}/summary` | Just the counts |
| `GET` | `/reconciliations/{id}/discrepancies?page=0&size=20` | Non-matched rows in file order, ledger-only rows last |
| `GET` | `/reconciliations/{id}/discrepancies/export` | All non-matched rows as a CSV download |
| `POST` | `/ledger-imports` | Upload a ledger CSV (202; 409 while anything else is running) |
| `GET` | `/ledger-imports?page=0&size=20` | Ledger imports, newest first |
| `GET` | `/ledger-imports/{id}` | One import with its counts |
| `GET` | `/ledger-imports/{id}/errors?page=0&size=20` | Rejected ledger lines with reasons |
| `GET` | `/reconciliations/{id}/suggestions` | Suggested pairs of a run |
| `POST` | `/reconciliations/{id}/suggestions/{sid}/confirm` | "Same payment" (409 if already decided) |
| `POST` | `/reconciliations/{id}/suggestions/{sid}/reject` | "Not the same", optional `{"note": "..."}` |
| `POST` | `/gateway-formats/suggest` | Suggest a mapping for a sample CSV (nothing saved) |
| `POST` | `/gateway-formats` | Save a reviewed mapping (formats can't be edited) |
| `GET` | `/gateway-formats` | Saved formats |
| `POST` | `/reconciliations?formatId={id}` | Upload a file in a saved format |
| `GET` | `/auth/me` | Who am I |

## Tests

```bash
cd backend && ./mvnw verify      # needs Docker running (Testcontainers)
cd frontend && npm test && npm run build
```

GitHub Actions runs both on pushes to `main` and on pull requests.

## Performance

The ledger used to be queried once per CSV row: 100,000 queries for a 100,000-row file. It is now queried once per chunk: about 1,000 queries. With 1 ms of network delay between the app and the database, a 100,000-row file went from about 170 seconds to about 23 seconds. See [benchmark results](docs/BENCHMARK_RESULTS.md) and `scripts/` to re-run it.

## Scope: what is deliberately not built

These would push the project from "reconciliation tool" to "reconciliation product", and each needs its own design work:

- One-to-many matching (one ledger row paid as several gateway rows): this is the subset-sum problem
- Netting out gateway fees, currencies, amounts like `₹1,23,456.00`
- Excel files or several sheets
- Applying an AI mapping without an analyst confirming it, or learning from corrections
- More than one AI provider (the `AiColumnMappingSuggester` interface makes adding one easy)

## What I'd improve next

- A gateway column on the ledger, so several gateway files covering the same days can each be checked in both directions
- A database lock around "is anything running?" + "start", so two requests in the same millisecond can't both pass the check
- A "retry" button for failed runs (today: upload the same file again)
- Clean up old uploaded files
- Frontend component tests

## License

MIT, see [LICENSE](LICENSE).
