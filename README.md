# BalanceTrail

BalanceTrail checks a payment gateway's settlement file against a company's internal ledger and shows every transaction that doesn't line up.

An analyst uploads a CSV. A Spring Batch job reads it in chunks of 100 rows, compares each row with the ledger, and records one result per line. The dashboard shows the totals and a paginated list of discrepancies.

Built by **Yashwardhan Verma** · [GitHub](https://github.com/YashwardhanV) · [LinkedIn](https://www.linkedin.com/in/yashwardhanv)

**Stack:** Java 21, Spring Boot 3.5, Spring Batch, Spring Data JPA, Spring Security, PostgreSQL 17, Flyway, React 19, TypeScript, Tailwind CSS, Docker Compose, GitHub Actions.

## Features

- Upload a gateway CSV and follow the job while it runs (the UI polls every 2 seconds)
- Every line gets exactly one outcome: `MATCHED`, `AMOUNT_MISMATCH`, `MISSING_IN_LEDGER`, `INVALID` or `DUPLICATE`
- Uploading the same file twice returns the original run instead of processing it again (SHA-256 of the file)
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
                               2. same file uploaded before? → return that run (200)
                               3. save run as PENDING
                               4. start job on a background thread → return 202
                                          │
                                          ▼
                     Spring Batch job (one step, chunks of 100 rows)
                     reader:    CSV line → RawGatewayRecord
                     processor: validate → duplicate? → look up ledger → match
                     writer:    save 100 results in one transaction
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

Upload `backend/src/main/resources/demo/gateway-transactions.csv` to see all five outcomes.

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

## CSV format

```csv
transaction_id,account_number,amount,transaction_date
TXN-1001,ACC-1001,1250.00,2026-07-01
```

- `transaction_id`: required, up to 64 characters
- `account_number`: required, up to 32 characters
- `amount`: greater than zero, at most 2 decimal places
- `transaction_date`: `yyyy-MM-dd`

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/reconciliations` | Upload a CSV (202 new run, 200 same file again) |
| `GET` | `/reconciliations?page=0&size=20` | My runs, newest first |
| `GET` | `/reconciliations/{id}` | One run with its counts |
| `GET` | `/reconciliations/{id}/summary` | Just the counts |
| `GET` | `/reconciliations/{id}/discrepancies?page=0&size=20` | Non-matched rows in file order |
| `GET` | `/auth/me` | Who am I |

## Tests

```bash
cd backend && ./mvnw verify      # needs Docker running (Testcontainers)
cd frontend && npm test && npm run build
```

GitHub Actions runs both on pushes to `main` and on pull requests.

## Performance

A 100,000-row file took about 105 seconds (~950 rows/second) on a laptop. See [benchmark results](docs/BENCHMARK_RESULTS.md) and `scripts/` to re-run it.

## What I'd improve next

- Fetch the ledger rows for a whole chunk in one query (`findAllById`) instead of one query per row
- A "retry" button for failed runs
- Download discrepancies as CSV
- Frontend component tests

## License

MIT, see [LICENSE](LICENSE).
