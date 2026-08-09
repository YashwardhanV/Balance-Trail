# BalanceTrail Interview Guide

Use this document to understand the project, not as a script to memorize. Open the referenced classes and trace one demo CSV row before putting the project on a resume.

## 1. Modular monolith and REST boundary

### Problem it solves

An analyst needs to submit files, track history, and inspect discrepancies. A batch job that only runs at startup cannot provide that application workflow.

### How it works

`ReconciliationController` exposes create/list/detail/summary/discrepancy resources. Controllers accept HTTP concerns and call `ReconciliationService`; responses use records in `dto` rather than serializing JPA entities. Pagination has a maximum size of 100, creation returns `202`, an idempotent replay returns `200`, and missing/non-owned IDs return `404`.

### Important classes

- `ReconciliationController`
- `ReconciliationService`
- `PageResponse`, `ReconciliationRunResponse`, `DiscrepancyResponse`
- `ApiExceptionHandler`

### Tables

`reconciliation_run`, `reconciliation_item`, and `app_user`.

### Alternatives considered

- Microservices were rejected because there is one cohesive domain, one team/developer, and no independent scaling or deployment need.
- Returning entities directly was rejected because it couples persistence shape/lazy relations to the public contract.
- WebSocket progress was rejected because two-second polling is enough for infrequent local batch runs.

### Failure cases

Bad pagination values become `400`; a missing run is `404`; unexpected exceptions are logged but internal details are not returned.

### Tradeoffs

Polling creates small repeated GET traffic. It is much easier to run and explain than maintaining WebSocket connection state.

### Likely interviewer questions

**Why is this not basic CRUD?** The main write is an asynchronous, restartable batch pipeline with validation, domain matching, skips/retries, per-line evidence, idempotency, and transactional lifecycle management. CRUD endpoints only expose its state.

**Why a modular monolith?** The domain does not justify network boundaries. Packages enforce responsibility boundaries while one transaction/database and one deployment keep operations realistic for the project size.

**Why `202 Accepted`?** The upload transaction finishes before the batch job does. `202` tells the client work was accepted but is not complete and provides a resource to poll.

## 2. Spring Batch chunk pipeline

### Problem it solves

CSV files should be processed incrementally, with checkpoints and bounded transaction size, rather than loaded fully into memory or wrapped in one long transaction.

### How it works

`gatewayCsvReader` reads one physical line at a time. `ReconciliationItemProcessor` validates, detects duplicates, looks up the ledger, and returns a result entity. `JpaItemWriter` writes a configurable chunk (100 by default) in one transaction. Spring Batch stores job/step execution and checkpoint state in PostgreSQL.

### Important classes

- `BatchConfiguration`
- `GatewayCsvLineMapper`
- `ReconciliationItemProcessor`
- Spring Batch `FlatFileItemReader` and `JpaItemWriter`

### Tables

`reconciliation_item`, `ledger_transaction`, and Spring Batch `BATCH_*` metadata tables.

### Alternatives considered

- A controller loop was rejected because it lacks standard checkpoint, retry/skip, and execution metadata behavior.
- One transaction per row adds commit overhead.
- One transaction for the file holds locks/resources too long and loses all progress on a late failure.
- Partitioning was rejected until measurement shows the single-threaded step is insufficient.

### Failure cases

File disappearance fails the reader and the run. Invalid row values follow the skip path. A permanent database failure rolls back the current chunk while earlier committed chunks remain.

### Tradeoffs

Chunk size balances fewer commits against larger rollback units and memory. It is configurable so benchmarks can test it; 100 is a default, not a universal optimum.

### Likely interviewer questions

**What exactly is a chunk transaction?** The reader/processor produce up to the commit interval, the writer persists them, and the transaction commits. On a retryable failure that chunk is rolled back and attempted again; earlier chunks stay committed.

**What makes the reader restartable?** It has a stable name, `saveState(true)`, a file job parameter, and the Spring Batch execution context records its position after commits.

**Why keep the step single-threaded?** Input order matters for the first-occurrence-wins duplicate rule. It also avoids thread-safety requirements in the file reader and detector. Parallelism should follow a benchmark and a defined ordering rule.

## 3. Matching and discrepancy evidence

### Problem it solves

Operations needs more than a pass/fail count: it must know which line failed, what values differed, and why.

### How it works

`TransactionMatcher` is a pure domain component. A missing ledger ID becomes `MISSING_IN_LEDGER`; a numerically different `BigDecimal` becomes `AMOUNT_MISMATCH`; equality becomes `MATCHED`. The stored item includes CSV line, gateway values, ledger values, status, and reason.

### Important classes

- `TransactionMatcher`
- `GatewayTransaction`
- `ReconciliationItemEntity`
- `DiscrepancyResponse`

### Tables

`ledger_transaction` and `reconciliation_item`.

### Alternatives considered

- Doing comparison in the controller mixes transport and domain behavior.
- Comparing `BigDecimal` with `equals` was rejected because scale differs (`10.0` versus `10.00`) even when money is numerically equal; `compareTo` is used.
- Automatic fuzzy matching was rejected because it creates ambiguous financial outcomes and needs explicit business approval.

### Failure cases

Missing, blank, non-positive, or over-precision amounts are invalid. Account/date are retained but do not silently change the match key.

### Tradeoffs

There is one primary-key ledger lookup for each valid unique row. It is simple and index-backed but can become the dominant large-file cost.

### Likely interviewer questions

**Why not match on account, date, and amount?** The modeled contract defines the external transaction ID as the correlation key. Adding fallback matching can create false positives; it should be a separate reviewed rule with tests and possibly an `AMBIGUOUS` status.

**Why persist matched rows too?** They provide a complete audit of the input and allow counts to be reconstructed. The storage cost is acceptable at the demonstrated scale.

## 4. Skip, retry, and failure-aware summary

### Problem it solves

Bad business input should not abort a good file, while transient infrastructure errors deserve a limited retry. Permanent failures must remain visible.

### How it works

`InvalidRecordException` and `DuplicateTransactionException` are skippable. `ReconciliationSkipListener` persists an `INVALID` or `DUPLICATE` item through `SkippedItemRecorder` in `REQUIRES_NEW`. `TransientDataAccessException` uses a bounded retry count and calculated exponential delay. The summary step runs after any main-step exit; the job listener marks the application run failed if the main step failed.

### Important classes

- `BatchConfiguration`
- `RetryDelayCalculator`, `CalculatedBackOffPolicy`
- `ReconciliationSkipListener`, `SkippedItemRecorder`
- `ReconciliationSummaryTasklet`, `ReconciliationJobListener`

### Tables

`reconciliation_item`, `reconciliation_run`, and `BATCH_STEP_EXECUTION`/related metadata.

### Alternatives considered

- Retrying invalid rows is pointless because the same data will not become valid.
- Skipping every exception could hide programming/schema failures.
- Log-only skips were rejected because the dashboard and audit need durable row evidence.

### Failure cases

The configurable skip limit is a safety ceiling. Crossing it fails the step. Interrupted backoff restores the interrupt flag and fails instead of swallowing shutdown. A summary failure also leaves a failed/incomplete lifecycle visible through the listener/launcher handling.

### Tradeoffs

Each skipped row uses an additional short transaction. Bad-data-heavy files therefore cost more, which is acceptable because reliable evidence is more important than optimizing pathological inputs.

### Likely interviewer questions

**Retry versus skip?** Retry is for a potentially temporary technical failure; skip is for a known row-level business/data problem that will not improve on immediate repetition.

**Why `REQUIRES_NEW` for skipped records?** The chunk transaction that saw the exception can roll back. A separate transaction ensures the reason survives. `(run_id, line_number)` prevents duplicate skip evidence.

**Does the summary hide a failed job?** No. It is deliberately attempted for partial visibility, then `ReconciliationJobListener` inspects the reconciliation step and sets the application run to `FAILED` if that step failed.

## 5. File and transaction idempotency

### Problem it solves

Gateway files can be resent, users can double-click, and a file can repeat a transaction. Processing these as new accepted data produces misleading counts or double effects.

### How it works

`FileStorageService` calculates SHA-256 while streaming the upload to disk. `RunPersistenceService` checks and relies on unique `(owner_id, file_sha256)`. Repeated content returns the original run without launching another job. Within a run, `DuplicateDetector` uses a set; on restart it preloads committed accepted IDs. A partial PostgreSQL unique index remains the final accepted-result guard.

### Important classes

- `FileStorageService`
- `RunPersistenceService`, `ReconciliationService`
- `DuplicateDetector`
- `V1__create_business_schema.sql`

### Tables

`reconciliation_run` and `reconciliation_item`.

### Alternatives considered

- File name is not a reliable idempotency key because the same content can be renamed and a name can be reused for different content.
- An in-memory check alone does not protect concurrent requests or restarts.
- A global unique gateway ID was rejected because the same external ID may legitimately appear in separate run history; uniqueness is scoped to one run.

### Failure cases

Two concurrent identical uploads can race past the first query. The database constraint selects one winner; the outer service catches the conflict, removes the redundant file, and returns the winner.

### Tradeoffs

SHA-256 makes upload work linear in file size, which is unavoidable because the bytes must be read anyway. The per-run ID set uses memory proportional to accepted unique IDs.

### Likely interviewer questions

**Is SHA-256 alone enough?** No. The unique database constraint makes it atomic. The hash is the key representation; the constraint enforces it under concurrency.

**What does idempotent mean here?** Repeating identical input for the same owner returns the same reconciliation resource and does not create a second batch execution or result set.

## 6. Transactional asynchronous launch

### Problem it solves

Returning only after a large job completes gives poor API behavior, but starting a worker before the run insert commits creates a visibility race.

### How it works

`RunPersistenceService` inserts `PENDING` and publishes `ReconciliationCreatedEvent` in its transaction. `BatchLaunchListener` observes it at `AFTER_COMMIT`, submits to a bounded `ThreadPoolTaskExecutor`, and invokes Spring Batch with unique `runId`, `inputFile`, and submission parameters.

### Important classes

- `RunPersistenceService`
- `ReconciliationCreatedEvent`
- `BatchLaunchListener`
- `BatchAsyncConfiguration`

### Tables

`reconciliation_run` and Spring Batch job metadata.

### Alternatives considered

- Calling the job before commit risks “run not found.”
- An unbounded async executor risks memory/thread exhaustion.
- Kafka/RabbitMQ would improve durable dispatch but adds infrastructure disproportionate to a one-node portfolio app.

### Failure cases

Queue rejection or launch exception marks the already committed run `FAILED`. A process crash in the small window after commit but before in-memory submission can leave `PENDING`; this is documented rather than hidden.

### Tradeoffs

The in-memory event is not a durable queue. A future scheduled recovery query for stale `PENDING` rows is the smallest improvement before considering messaging.

### Likely interviewer questions

**Why not `@Async` directly?** An explicit named bounded executor makes capacity and shutdown behavior visible. The important part is the after-commit boundary, not the annotation.

**Is the launch exactly once?** No distributed system claim is made. Database file idempotency and Spring Batch job parameters prevent duplicate business runs in normal operation, but a crash-recovery dispatcher would be required for durable at-least-once launch.

## 7. Relational model, indexes, and migrations

### Problem it solves

Batch metadata, ownership, audit rows, uniqueness, and paginated discrepancy queries require durable relational invariants.

### How it works

Flyway owns the schema and Hibernate validates it. Foreign keys connect users, runs, items, and optional ledgers. Check constraints restrict statuses/counts and positive ledger amounts. Composite indexes match actual query filters/order. Item IDs use a sequence allocation of 100 to permit JDBC insert batching.

### Important files/classes

- `V1__create_business_schema.sql`, `V2__seed_demo_ledger.sql`
- All classes under `entity` and `repository`
- `application.yml` (`ddl-auto: validate`, JDBC batch size)

### Tables

All four business tables plus Spring Batch metadata.

### Alternatives considered

- `ddl-auto: create-drop` loses history and provides no reviewable schema evolution.
- NoSQL was rejected because relations, constraints, aggregates, and transactional chunks are central.
- Identity item IDs were avoided because identity generation can prevent effective insert batching.

### Failure cases

Schema/entity mismatch fails startup. Foreign keys reject orphan items. Constraints protect invariants even if a future code path omits an application check.

### Tradeoffs

The partial unique index is PostgreSQL-specific. PostgreSQL is already the declared database, and the clarity/integrity benefit outweighs portability here.

### Likely interviewer questions

**How did you choose indexes?** From query shapes: owner plus newest creation for history; run plus status plus line for discrepancies; primary-key transaction ID for matching. Indexes that support no current or plausible near-term query were not added.

**Why both Flyway and Hibernate validation?** Flyway deliberately changes the schema; Hibernate checks mappings agree but does not mutate it.

## 8. Authentication and ownership authorization

### Problem it solves

Reconciliation files contain financial data. One analyst must not inspect another analyst's run by guessing a UUID.

### How it works

Spring Security loads users from `app_user`; BCrypt stores password hashes. HTTP Basic is stateless. Service/repository methods fetch by both run ID and authenticated username. Public access is limited to health/OpenAPI.

### Important classes

- `SecurityConfiguration`
- `DatabaseUserDetailsService`
- `BootstrapUserConfiguration`
- Owner-scoped `ReconciliationRunRepository` methods

### Tables

`app_user`, `reconciliation_run`.

### Alternatives considered

- JWT was rejected because token issuing, rotation, refresh, and revocation do not teach the reconciliation domain and are easy to implement superficially.
- In-memory users do not exercise database-backed identity or ownership.
- Session login would require a deliberate CSRF/cookie flow; Basic is smaller for a same-origin local demo.

### Failure cases

Invalid credentials return `401`. Disabled users cannot authenticate. Another user's UUID returns `404` rather than revealing existence.

### Tradeoffs

Basic credentials are sent on every request and must be protected by HTTPS. The React app retains them only in memory, so a refresh requires login again.

### Likely interviewer questions

**Is HTTP Basic production safe?** The protocol is acceptable only over TLS; this project targets local execution. A public deployment should terminate HTTPS and likely use OIDC. I do not claim more than the implemented boundary.

**Why BCrypt strength 12?** It is a defensible local default that makes offline guessing costlier. Authentication volume here is tiny; the cost should still be measured for a high-login system.

## 9. Testing strategy

### Problem it solves

The risky behavior is not getters/setters; it is matching, parsing, retry calculation, constraints, authentication, chunk skip isolation, and rerun semantics.

### How it works

Unit tests isolate the CSV line mapper, processor validation/duplicates, matcher, and retry delay calculation. A PostgreSQL Testcontainer applies real Flyway SQL and exercises HTTP Basic, multipart APIs, job polling, summary/discrepancies, malformed rows, duplicates, mismatch, missing ledger, repeated upload, and database constraints.

### Important classes

- `GatewayCsvLineMapperTest`
- `ReconciliationItemProcessorTest`
- `TransactionMatcherTest`
- `RetryDelayCalculatorTest`
- `ReconciliationApplicationIntegrationTest`

### Alternatives considered

- H2 was rejected because PostgreSQL partial indexes, data types, and constraint behavior must be tested against the real engine.
- A coverage target was rejected as the goal; meaningful failure-path assertions are more valuable.
- WireMock is not used because there is no external HTTP dependency.

### Failure cases

Tests bound asynchronous waiting to ten seconds and fail explicitly. Testcontainers requires a reachable Docker daemon.

### Tradeoffs

Starting PostgreSQL makes integration tests slower than in-memory tests but catches schema and transaction behavior that mocks cannot.

### Likely interviewer questions

**What would you test next?** A forced transient repository failure proving the retry count/backoff integration, a forced writer failure proving partial summary state, owner-isolation with a second user, and a job restart from a committed checkpoint.

## 10. Containers, CI, and benchmarks

### Problem it solves

A reviewer should not manually install PostgreSQL or trust performance claims that cannot be reproduced.

### How it works

Compose builds backend/frontend, provisions PostgreSQL and volumes, orders startup by health checks, and exposes UI/API. GitHub Actions runs Maven/Testcontainers and the typed frontend build. The generator creates deterministic 1k/10k/100k category distributions; the PowerShell runner loads ledger data, executes multiple API runs, polls completion, and verifies repeated-import identity.

### Important files

- `docker-compose.yml`, both Dockerfiles, `.env.example`
- `.github/workflows/ci.yml`
- `scripts/generate_benchmark_data.py`, `scripts/run-benchmarks.ps1`
- `docs/BENCHMARK_RESULTS.md`

### Alternatives considered

- Kubernetes/Helm were rejected because one local deployment does not need orchestration.
- Fabricated or extrapolated metrics are prohibited.
- A microbenchmark alone was rejected because end-to-end CSV/database processing is the claim being evaluated.

### Failure cases

Benchmarks record failed status/counts rather than dropping a run. Environment, commands, date, volume, and number of runs are part of the result so numbers are not presented as universal.

### Tradeoffs

End-to-end timing includes API submission/poll granularity and local Docker overhead. That is acceptable because it is reproducible and close to reviewer experience; it should not be described as a pure algorithm benchmark.

### Likely interviewer questions

**Can these numbers predict production capacity?** No. They describe one dated local environment and dataset. They are evidence for this implementation, not a capacity plan.

**Why report median and p95?** Median reduces sensitivity to one noisy run. P95 is useful for request/load latency when enough samples exist; three batch samples are too few for a meaningful p95, so the report does not pretend otherwise.

## 11. React operations workspace

### Problem it solves

An operations user needs to start a run, monitor its lifecycle, compare outcome counts, and investigate exceptions without translating raw API responses.

### How it works

`Dashboard` owns the small amount of client state and polls only while a run is `PENDING` or `RUNNING`. `UploadPanel` submits one CSV through the authenticated API client. `RunTable` controls the selected run, while `StatCard` and `DiscrepancyTable` render its aggregate and paginated detail views. Credentials remain in memory and are cleared on sign-out or page refresh.

### Important files

- `frontend/src/Dashboard.tsx`
- `frontend/src/api.ts`
- `frontend/src/components/UploadPanel.tsx`
- `frontend/src/components/RunTable.tsx`
- `frontend/src/components/DiscrepancyTable.tsx`
- `frontend/src/components/BrandMark.tsx`
- `frontend/src/index.css`
- `docs/BRAND_AND_UX.md`

### Alternatives considered

- WebSocket/SSE was rejected because two-second polling only while work is active is simpler and sufficient for this local workflow.
- Redux or another state library was rejected because the screen has one shallow state owner and no complex cross-route state.
- A component library was rejected because the interface needs only a small set of accessible primitives.

### Failure cases

Authentication failures return the user to a clear error state. Upload and refresh errors are announced through an alert and do not erase the current run. An idempotent replay explains that the original run was opened. Empty, loading, processing, successful, and failed states each have separate UI copy.

### Tradeoffs

Credentials in memory are safer than local storage for this demo but require a new sign-in after refresh. Polling can make more requests than push updates, but its bounded active-only lifecycle is easier to explain and operate. Horizontal table scrolling preserves detail on phones but is less convenient than a dedicated mobile record view.

### Likely interviewer questions

**Why does the frontend poll instead of using WebSockets?** Runs are infrequent and coarse-grained. Polling every two seconds only while a job is active meets the UX need without connection lifecycle, reconnection, or server push infrastructure.

**How did you make status understandable without color alone?** Every status uses visible text plus a colored dot and contextual copy; table selection and focus also have non-color structure.

## Before putting this project on your resume

You should be able to do all of the following without this guide:

1. Trace a valid, invalid, and duplicate row through reader, processor, writer/listener, and summary.
2. Explain the transaction boundaries and what is committed when a chunk fails.
3. Explain why `BigDecimal.compareTo` is used for money equality.
4. Show the two database uniqueness rules that implement idempotency.
5. Explain why the after-commit event exists and name its crash window.
6. Write the SQL query/index shape behind discrepancy pagination.
7. Explain retry versus skip using one project example of each.
8. Defend Basic authentication's limited scope and the HTTPS requirement.
9. Run and interpret one Testcontainers integration test failure.
10. Re-run the benchmark and update resume bullets only from recorded results.
