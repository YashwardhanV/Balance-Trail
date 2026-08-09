# Resume bullets

Use only the version that matches the evidence you are prepared to discuss. Both versions contain exactly three bullets. Do not combine the strongest phrases from both versions unless the corresponding benchmark remains reproducible on the commit you submit.

## Version A — no benchmark metrics

- Built BalanceTrail, a React, TypeScript, Spring Boot, Spring Batch, and PostgreSQL modular monolith that accepts gateway CSV files, exposes authenticated reconciliation APIs, and presents run history, summaries, and paginated discrepancy data.
- Implemented chunk-oriented transaction matching with validation, amount and missing-ledger classifications, duplicate detection, retry/skip policies, and database-backed SHA-256 idempotency without adding queues, caches, or distributed infrastructure.
- Added Flyway-managed constraints and indexes, centralized Problem Details error handling, DTO-based REST boundaries, 17 JUnit/Testcontainers tests, Docker Compose, health checks, demo data, and a GitHub Actions build.

## Version B — benchmarked

- Built BalanceTrail, a React, TypeScript, Spring Boot, Spring Batch, and PostgreSQL modular monolith with authenticated CSV upload, five reconciliation APIs, run history, summary cards, and paginated discrepancy review.
- Implemented chunk processing for exact matches, amount mismatches, missing ledger records, malformed rows, and duplicates, preserving all five outcome counts across three deterministic 100,000-record runs.
- Processed 100,000 reconciliation rows in a measured median of 104.949 seconds (952.84 records/second), with 0 failed runs across three samples and all repeated imports returning the original run ID instead of launching another job.

## Evidence map

| Claim | Evidence |
|---|---|
| Modular-monolith stack and APIs | `README.md`, `docs/ARCHITECTURE.md`, `docs/API.md`, source tree |
| Chunk matching, validation, retry, skip, and duplicate behavior | `BatchConfiguration`, `ReconciliationItemProcessor`, `DuplicateDetector`, `ReconciliationSkipListener`, unit/integration tests |
| Database idempotency | SHA-256 handling in `FileStorageService` and `ReconciliationService`; unique owner/content-hash constraint in Flyway V1 |
| 17 tests | Maven Surefire test result from the final test run; test sources under `backend/src/test` |
| 100,000-record timing and counts | `docs/BENCHMARK_RESULTS.md` and `benchmark-data/results-20260807-090413.json` |

Re-run the suite if code, container resources, database version, chunk size, or matching strategy changes. A benchmark from an older implementation is not evidence for a newer one.
