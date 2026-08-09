# SDE-1 project audit

## Executive assessment

BalanceTrail is a strong SDE-1 / Associate Software Engineer portfolio project. It is deeper than tutorial CRUD because its central workflow is a restartable, chunk-oriented batch pipeline with correctness classifications, skip isolation, retry behavior, owner-scoped APIs, idempotency, relational constraints, and measured performance. It remains believable for one fresher because it is one Spring Boot application, one React application, and one PostgreSQL database.

Overall assessment: **8.3 / 10**. The project is resume-ready after the owner can explain the topics in `docs/INTERVIEW_GUIDE.md` without memorized phrasing.

## Scorecard

| Area | Score | Evidence and remaining gap |
|---|---:|---|
| Java fundamentals | 8/10 | Records, enums, exceptions, interfaces, immutable DTOs, collections, `BigDecimal`, date parsing, and focused domain services are used well. The project does not need advanced language tricks. |
| Spring Boot knowledge | 9/10 | MVC, Validation, Data JPA, Security, Batch, Actuator, configuration properties, transactions, events, and centralized errors are integrated coherently. |
| REST API design | 8/10 | Resource-oriented routes, `202 Accepted`, `201 Created`, pagination, DTOs, owner scoping, OpenAPI, and Problem Details are present. HTTP Basic is deliberately simple and not a production session design. |
| Database design | 9/10 | Normalized business tables, foreign keys, checks, unique constraints, partial indexes, sequence allocation, optimistic versioning, and Flyway migrations provide strong evidence. |
| Transaction knowledge | 8/10 | Run creation commits before asynchronous launch; chunks define processing transaction boundaries; skipped evidence uses `REQUIRES_NEW`; job status updates are isolated. Be prepared to explain partial progress. |
| Testing | 8/10 | 17 passing unit/integration tests cover matching, retries, malformed CSV, duplicates, amount mismatch, missing ledger rows, rerun idempotency, authentication, REST behavior, and PostgreSQL constraints. Frontend component tests are a reasonable future addition. |
| Reliability | 8/10 | Idempotency, database invariants, skip/retry policies, restart metadata, bounded execution, health checks, and failure summaries are present. There is no crash-recovery end-to-end test across a process restart. |
| Frontend integration | 7/10 | The React/TypeScript dashboard performs upload, polling, run selection, summary rendering, discrepancy pagination, error display, and accessible form behavior. It intentionally avoids complex client state management. |
| Deployment/run experience | 8/10 | Dockerfiles, Compose health ordering, environment configuration, seed data, one-command startup, Actuator, and CI are included. This is local deployment, not a cloud production claim. |
| Interview defensibility | 10/10 | Analysis, architecture, ER/API docs, benchmark evidence, tradeoffs, limitations, and the interview guide make every major decision traceable. Actual defensibility depends on the candidate understanding them. |

## 1. Is anything still unnecessarily SDE-2/SDE-3?

No major infrastructure is unnecessarily senior-level. There are no microservices, brokers, caches, Kubernetes manifests, service discovery, tracing stacks, CQRS, or multiple databases.

Three mechanisms may sound advanced but are proportionate to this domain:

- **Spring Batch** is the learning objective, not resume-keyword decoration. It provides chunk transactions, restart metadata, skip/retry semantics, and job status.
- **After-commit asynchronous launch** prevents a worker from reading a run row before its creation transaction commits and lets upload return `202 Accepted`. It stays in-process and bounded; it is not a distributed worker system.
- **Partial unique index and SHA-256 idempotency** enforce business invariants under concurrency. Database correctness is appropriate SDE-1 depth.

If a reviewer wants an even smaller version, the bounded executor can use one thread. Do not remove chunking, idempotency, constraints, or the skip path.

## 2. Is anything too basic or tutorial-like?

The core backend is not tutorial CRUD. The main risks of looking basic are presentation and explanation:

- HTTP Basic is suitable for a local portfolio demo but must be described as a deliberate limitation, not a production authentication architecture.
- The dashboard has no frontend test suite and uses polling rather than WebSocket/SSE. Polling is correct for this scale; add UI tests before adding real-time infrastructure.
- Ledger data is seeded/imported directly for the demo. A future ledger-management workflow could be useful, but generic ledger CRUD would not improve the main signal.

The next valuable depth would be a restart/crash-recovery integration test or an experimentally benchmarked ledger prefetch optimization—not another technology.

## 3. Is every technology justified?

| Technology | Justification |
|---|---|
| Java 21 | Primary backend language and a supported LTS baseline; retained for reproducible tests and benchmarks even though Java 25 is the newer LTS. |
| Spring Boot / MVC | REST delivery, dependency injection, configuration, validation, and operational conventions. |
| Spring Batch | Central chunk-oriented ingestion, restart, retry, skip, and job metadata capability. |
| Spring Data JPA / Hibernate | Business persistence and repository queries without exposing entities at the API boundary. |
| Spring Security | Authenticated, role-checked, owner-scoped access to potentially sensitive reconciliation data. |
| PostgreSQL | Relational joins, money/date types, constraints, partial indexes, and durable idempotency. |
| Flyway | Repeatable, reviewable schema and seed migrations. |
| React / TypeScript | Small reviewer-facing workflow with compile-time client contracts. |
| Tailwind CSS | Compact, consistent dashboard styling; no runtime architecture consequence. |
| Docker / Compose | One-command, reproducible app plus database startup. |
| JUnit 5 / Mockito / Testcontainers | Fast domain tests plus real PostgreSQL integration evidence. Mockito is available but is not forced where plain fakes or real collaborators are clearer. |
| springdoc OpenAPI | Discoverable API contract and reviewer-friendly Swagger UI. |
| GitHub Actions | Basic build/test verification for both applications. |

Every included technology has a direct job. No technology exists solely for a keyword.

## 4. Can every resume bullet be proven?

Yes, provided the bullets are copied exactly from `docs/RESUME_BULLETS.md` and kept with the implementation that produced them.

- Version A claims are directly inspectable in source, migrations, test reports, Compose, and CI configuration.
- Version B's 100,000-record median, throughput, failure count, expected classifications, and idempotent replays appear in `docs/BENCHMARK_RESULTS.md` and the raw JSON.
- The project does **not** claim a percentage improvement, user count, p95, high availability, production readiness, horizontal scale, or perfect security.

If a change affects the pipeline, database, image versions, chunk size, or resources, rerun the benchmark before retaining Version B.

## 5. Ten most likely interview questions

1. **Why did you keep Spring Batch instead of using a controller loop?**  
   Chunk transactions, restart metadata, item-level transformation, skip/retry policies, and observable job state are central to reliable file processing.

2. **How is uploading the same CSV idempotent?**  
   The server streams the file while computing SHA-256, then looks up and enforces a unique `(owner_id, content_hash)` pair. A repeated upload returns the existing run and does not launch a job.

3. **What happens when one CSV row is malformed?**  
   The line mapper preserves the raw row and parse error; the processor throws a skippable domain exception; the skip listener stores an `INVALID` item in a separate transaction; later rows continue.

4. **How do you distinguish an amount mismatch from a missing ledger transaction?**  
   The processor first resolves the ledger transaction by external transaction ID. Absence yields `MISSING_IN_LEDGER`; presence with a different `BigDecimal` value yields `AMOUNT_MISMATCH`; equal values yield `MATCHED`.

5. **Why is duplicate detection implemented in memory and in the database?**  
   The step-scoped detector provides a useful row-level duplicate result within one file. Database uniqueness is the final concurrency-safe invariant and protects against retries or implementation mistakes.

6. **Where are the transaction boundaries?**  
   Run creation is one service transaction, each Spring Batch chunk is a transaction, skipped-item recording uses `REQUIRES_NEW`, and final summary/status updates use isolated service transactions.

7. **Why launch only after the creation transaction commits?**  
   An `AFTER_COMMIT` listener prevents the asynchronous worker from racing with the uncommitted run record. If creation rolls back, no batch job starts.

8. **What does retry and skip each solve?**  
   Retry is for transient database failures and uses bounded exponential delay. Skip is for deterministic bad input such as malformed or duplicate rows; retrying those would not help.

9. **What did the benchmark actually prove?**  
   On the documented local environment, three sequential 100k runs had a 104.949-second median at 952.84 records/second, correct outcome totals, zero failed runs, and all reuploads returned the original run. It does not prove multi-user scale or production capacity.

10. **What would you improve first and how would you verify it?**  
    Profile and experiment with eliminating the per-record ledger query, likely by bounded prefetch or a staged database-side join. Run the same deterministic datasets before and after, compare raw medians, and retain correctness tests.

## 6. What must be understood before putting this on a resume?

Be able to explain and sketch, without reading the code:

- the upload-to-completion sequence and why `202 Accepted` is used;
- every reconciliation outcome and why counts always total the input;
- reader, processor, writer, chunk, retry, skip, listener, and tasklet responsibilities;
- why the duplicate detector remembers the first line and permits the same physical line during a chunk retry;
- SHA-256 idempotency and why the database constraint is still required after an application lookup;
- JPA entities versus DTOs and why controllers never return entities;
- foreign keys, check constraints, unique/partial indexes, and the queries they support;
- transaction boundaries, rollback effects, and what persists after a skipped row;
- authentication versus authorization and how owner scoping prevents cross-user reads;
- what the benchmark measured, what it did not measure, and why the first contaminated attempt was discarded;
- the known per-row ledger-query bottleneck and a testable optimization hypothesis;
- why Kafka, Redis, microservices, WebSockets, and Kubernetes are absent.

Finally, run the demo yourself, inspect a discrepancy, run the tests, and rerun at least the 1,000-record benchmark. If any concise answer above feels memorized rather than understood, use `docs/INTERVIEW_GUIDE.md` and trace the named classes until you can explain the behavior in your own words.
