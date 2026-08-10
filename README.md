# BalanceTrail

BalanceTrail is a transaction-reconciliation portfolio application built as a modular monolith with Java 21, Spring Boot, Spring Batch, PostgreSQL, React, TypeScript, and Tailwind CSS. Its product promise is simple: **every transaction, clearly accounted for.**

Project author: **Yashwardhan Verma**

[GitHub](https://github.com/YashwardhanV) · [LinkedIn](https://www.linkedin.com/in/yashwardhanv) · [Email](mailto:yashwardhanverma108@gmail.com)

It accepts a payment-gateway CSV, validates each row, compares it with the internal ledger, isolates invalid/duplicate rows, and exposes a durable run summary and discrepancy table. The project is intentionally one understandable application—not a collection of resume-keyword infrastructure.



## What problem it solves

Payment providers can send settlement files that disagree with a company's ledger. Operations needs to know:

- which transactions matched;
- which have different amounts;
- which are absent from the ledger;
- which input rows are invalid or duplicated;
- whether a repeated file was already processed; and
- whether a run completed, completed with skips, or failed.

## Main features

- Authenticated CSV upload and asynchronous reconciliation lifecycle
- Spring Batch `ItemReader` / `ItemProcessor` / `ItemWriter` chunk pipeline
- `MATCHED`, `AMOUNT_MISMATCH`, `MISSING_IN_LEDGER`, `INVALID`, and `DUPLICATE` outcomes
- Bounded retry with exponential delay for transient data-access failures
- Row-level skip handling with durable error evidence
- SHA-256 file idempotency plus database uniqueness constraints
- Restart-aware duplicate detection and Spring Batch metadata in PostgreSQL
- Paginated run history and discrepancy APIs using DTOs
- Flyway migrations, check constraints, foreign keys, and query-driven indexes
- Database-backed BCrypt user authentication and owner-scoped authorization
- Responsive React operations workspace with CSV import, polling state, outcome summaries, run history, and discrepancy review
- PostgreSQL Testcontainers integration tests, Docker Compose, health checks, OpenAPI, and CI
- Deterministic 1,000 / 10,000 / 100,000 record benchmark utilities

## Architecture

```text
Browser
  -> React dashboard served by Nginx
  -> authenticated REST calls
  -> one Spring Boot backend
       -> upload/hash/run services
       -> Spring Batch chunk job
       -> Spring Data JPA
  -> one PostgreSQL database
```

The uploaded file is stored in a Docker volume. A `PENDING` run and its after-commit launch event are created in one transaction. A bounded local executor launches the Spring Batch job; the client receives `202 Accepted` and polls the run resource.

Detailed diagrams and transaction/failure reasoning are in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Database model

- `app_user`: login, BCrypt hash, role
- `ledger_transaction`: internal source-of-truth transaction
- `reconciliation_run`: file identity, owner, lifecycle, persisted counters
- `reconciliation_item`: one outcome per physical CSV line
- Spring Batch `BATCH_*`: job, step, execution context, and restart metadata

The business ER diagram and constraint/index rationale are in [docs/ER_DIAGRAM.md](docs/ER_DIAGRAM.md).

## Important engineering decisions

| Decision | Reason |
|---|---|
| Modular monolith | One cohesive domain does not need network/deployment boundaries |
| Keep Spring Batch | Chunk transactions, checkpoints, skip/retry, and execution metadata are core to the problem |
| PostgreSQL-only persistence | Relations, constraints, aggregation, and transactional batch metadata fit naturally |
| File hash scoped by owner | Identical bytes are a better idempotency key than a mutable file name |
| Single-threaded step | Deterministic first-occurrence duplicate semantics and simpler restart behavior |
| Bounded async run executor | Responsive API without unbounded thread creation |
| Polling rather than WebSocket | Adequate status UX with much lower lifecycle complexity |
| Flyway + Hibernate validation | Reviewable migrations with mapping drift detected at startup |
| Testcontainers, not H2 | Tests the actual PostgreSQL types, partial index, constraints, and transactions |
| HTTP Basic for local demo | Small, inspectable Spring Security boundary; HTTPS/OIDC is required for a hosted version |
| Bespoke UI without a component library | The workflow needs a small accessible design system, not another runtime dependency |

## Run in one command

Requirements: Docker Desktop/Engine with Compose.

```bash
docker compose up --build
```

Then open:

- Dashboard: <http://localhost:3000>
- Backend API: <http://localhost:8080>
- OpenAPI UI: <http://localhost:8080/swagger-ui.html>
- Health: <http://localhost:8080/actuator/health>

Default local login:

```text
username: analyst
password: change-me-now
```

Copy `.env.example` to `.env` and change the password before sharing a running environment. Bootstrap credentials are used only when that username does not already exist in the persistent database volume.

To see useful behavior immediately, upload:

```text
backend/src/main/resources/demo/gateway-transactions.csv
```

It includes matches, an amount mismatch, a missing transaction, a duplicate, an invalid amount, and an invalid date.

Stop containers without deleting data:

```bash
docker compose down
```

`docker compose down -v` intentionally deletes database and upload volumes; use it only when you want a clean reset.

## Development commands

Start only PostgreSQL:

```bash
docker compose up -d db
```

Run the backend (Java 21):

```bash
cd backend
./mvnw spring-boot:run
```

On Windows Command Prompt/PowerShell use `mvnw.cmd spring-boot:run`.

Run the frontend (Node 24):

```bash
cd frontend
npm ci
npm run dev
```

Vite proxies API routes to `localhost:8080`.

## Java version decision

The project deliberately targets Java 21. Java 25 is the newest LTS release, but upgrading solely to display a larger version number does not add meaningful SDE-1 signal and would make the recorded Java 21 benchmarks non-comparable. Java 21 remains an LTS release and is the version used by the backend build/runtime containers and CI.

Your host JDK does not affect the recommended Docker workflow. A machine with JDK 26 can run `docker compose up --build` because Maven and the application execute inside Java 21 containers. For local, non-Docker Maven development, use JDK 21 for the same environment as CI. See [docs/JAVA_VERSION.md](docs/JAVA_VERSION.md) for the compatibility decision and upgrade checklist.

## CSV contract

The exact header is:

```csv
transaction_id,account_number,amount,transaction_date
```

- `transaction_id`: required, at most 64 characters
- `account_number`: required, at most 32 characters
- `amount`: positive decimal with at most two fractional digits
- `transaction_date`: ISO date (`yyyy-MM-dd`)

Quoted single-line fields and doubled quote escaping are supported. Multiline quoted fields are not supported and are listed under known limitations.

## API examples

Create a run:

```bash
curl -i -u analyst:change-me-now \
  -F "file=@backend/src/main/resources/demo/gateway-transactions.csv" \
  http://localhost:8080/reconciliations
```

List history:

```bash
curl -u analyst:change-me-now \
  "http://localhost:8080/reconciliations?page=0&size=20"
```

Inspect one run and its discrepancies:

```bash
curl -u analyst:change-me-now http://localhost:8080/reconciliations/{id}
curl -u analyst:change-me-now http://localhost:8080/reconciliations/{id}/summary
curl -u analyst:change-me-now \
  "http://localhost:8080/reconciliations/{id}/discrepancies?page=0&size=20"
```

See [docs/API.md](docs/API.md) for contracts, error shape, and status codes.

## Tests

Backend tests require a running Docker daemon because integration tests start a real PostgreSQL container:

```bash
cd backend
./mvnw verify
```

The suite covers:

- matching and money comparison;
- retry delay calculation;
- quoted/malformed CSV mapping;
- processor validation and restart-aware duplicates;
- unauthenticated/authenticated API behavior;
- multipart creation, run polling, summaries, and discrepancies;
- valid, malformed, duplicate, mismatched, missing, and invalid rows;
- repeated identical input;
- chunk skip isolation; and
- PostgreSQL uniqueness/check constraints.

Frontend type-check and production build:

```bash
cd frontend
npm ci
npm run build
```

GitHub Actions runs both jobs on pushes and pull requests.

## Benchmarks

Start the Compose application, then run from PowerShell. The script runs the deterministic generator in a small Python container, so Docker remains the only runtime prerequisite:

```powershell
./scripts/run-benchmarks.ps1 -Sizes 1000,10000,100000 -Runs 3
```

The generator creates a deterministic distribution per 100 records: 80 matches, 10 amount mismatches, 7 missing ledger rows, 2 invalid rows, and 1 duplicate. Every measured processing run receives a unique prefix; an immediate second upload checks idempotent replay behavior.

Generated CSV files are ignored, while dated raw JSON is retained under `benchmark-data/` as evidence. Only actual results are summarized in [docs/BENCHMARK_RESULTS.md](docs/BENCHMARK_RESULTS.md). Do not copy a metric into a resume without re-running it on your environment and keeping the result file.

## Documentation

- [Complete documentation index](DOCUMENTATION_INDEX.md)
- [Local run guide](LOCAL_RUN_GUIDE.md)
- [Project author](AUTHORS.md)
  
- [Architecture and engineering decisions](docs/ARCHITECTURE.md)
- [Brand and UX rationale](docs/BRAND_AND_UX.md)
- [ER diagram and constraints](docs/ER_DIAGRAM.md)
- [API guide](docs/API.md)
- [Java version decision](docs/JAVA_VERSION.md)
- [Benchmark results](docs/BENCHMARK_RESULTS.md)
- [Interview guide](docs/INTERVIEW_GUIDE.md)
- [Evidence-bounded resume bullets](docs/RESUME_BULLETS.md)
- [Final SDE-1 audit](SDE1_AUDIT.md)

## Known limitations

- HTTP Basic is intended for localhost and requires HTTPS outside it; there is no password-management UI.
- Uploaded files remain on local volume for restart/audit and have no retention job.
- Dispatch after database commit uses an in-memory executor. A process crash in that narrow window can leave a stale `PENDING` run.
- The item step is intentionally single-threaded and performs one indexed ledger lookup per valid unique row.
- The duplicate detector holds accepted transaction IDs in memory for each active run.
- Only comma-delimited UTF-8, one-record-per-line CSV is supported.
- No cancellation/restart administration API is exposed.
- The frontend keeps credentials in memory, so a browser refresh requires signing in again.
- This is a portfolio application, not a claim of regulatory compliance or production readiness.

## Future improvements, in order

1. Add a small stale-`PENDING` recovery job and explicit operator restart endpoint.
2. Add upload retention/cleanup with an auditable policy.
3. Add owner-isolation and forced transient-failure integration tests.
4. Profile the per-row ledger lookup and evaluate chunk-level bulk fetch only if benchmarks justify it.
5. Add hosted HTTPS and OIDC if the application is deployed publicly.
6. Consider partitioning only after a measured requirement exceeds the single-process design.

Kafka, Redis, microservices, Kubernetes, and a distributed observability stack are not roadmap defaults.

## License and attribution

BalanceTrail's independently written code is copyright 2026 Yashwardhan Verma and is provided under the MIT License. 
