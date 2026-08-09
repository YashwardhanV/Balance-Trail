# Benchmark results

This file records measurements produced by the repository's benchmark utilities. The numbers below are observations from one local environment, not guarantees for other machines and not evidence of internet-scale capacity.

The later BalanceTrail naming and frontend refresh did not alter the measured Spring Batch processing path, database schema, dataset generator, or benchmark client. The retained measurements therefore describe the same backend implementation. Re-run the suite after any future batch, persistence, JVM, or database tuning change.

## Measurement date and environment

- Date: 2026-08-07 (Asia/Kolkata)
- Host OS: Microsoft Windows 11 Home Single Language, version 10.0.26200
- Host CPU: Intel Core i5-10200H at 2.40 GHz, 4 physical cores / 8 logical processors
- Host memory: 7.78 GiB visible RAM
- Docker Desktop allocation reported by the engine: 8 CPUs and 3,986,919,424 bytes (about 3.71 GiB) memory
- Docker Engine: 29.6.2
- Backend runtime: Eclipse Temurin Java 21 container
- Database: PostgreSQL 17.10 (Alpine, x86-64)
- Spring Batch configuration: chunk size 100; bounded executor with two worker threads
- Test method: one reconciliation at a time; no concurrent load clients

The first benchmark attempt was discarded because a timed-out client process remained alive and caused two 100,000-record jobs to overlap. The backend and client runners were stopped, the backend was restarted, and the complete matrix below was rerun sequentially. Only the clean rerun is reported.

## Dataset

`scripts/generate_benchmark_data.py` creates deterministic, realistic gateway and ledger CSV files. Every group of 100 gateway rows contains:

- 80 exact matches
- 10 amount mismatches
- 7 gateway transactions missing from the ledger
- 2 invalid gateway rows
- 1 duplicate transaction ID

Each measured file is unique, so a run cannot accidentally reuse an earlier result. Immediately after completion, the script uploads the same bytes again to test content-hash idempotency.

## Command

From the project root with the Docker Compose stack healthy:

```powershell
.\scripts\run-benchmarks.ps1 -Sizes 1000,10000,100000 -Runs 3
```

The clean raw result was written to `benchmark-data/results-20260807-090413.json` and is retained as evidence. Generated gateway/ledger CSV files are Git-ignored because they are reproducible.

## Results

`Processing` is the difference between the run's server-side `startedAt` and `finishedAt` timestamps. `End-to-end` starts before upload and stops when the polling client observes a terminal state. Throughput is `totalCount / processingSeconds`.

| Records | Runs | Processing seconds (individual) | Processing average | Processing median | End-to-end average | End-to-end median | Throughput average | Throughput median | Failures |
|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|
| 1,000 | 3 | 3.767, 2.872, 2.588 | 3.076 s | 2.872 s | 3.860 s | 3.477 s | 333.35 records/s | 348.19 records/s | 0 |
| 10,000 | 3 | 13.588, 10.992, 10.529 | 11.703 s | 10.992 s | 12.233 s | 11.374 s | 865.15 records/s | 909.75 records/s | 0 |
| 100,000 | 3 | 104.949, 111.703, 96.609 | 104.420 s | 104.949 s | 105.290 s | 105.950 s | 961.06 records/s | 952.84 records/s | 0 |

The suite has only three samples per size. A p95 value would not be statistically useful, so none is claimed.

## Correctness counts

Every run finished as `COMPLETED_WITH_SKIPS` and produced the deterministic expected counts:

| Records | Matched | Amount mismatch | Missing in ledger | Invalid | Duplicate | Count total |
|---:|---:|---:|---:|---:|---:|---:|
| 1,000 | 800 | 100 | 70 | 20 | 10 | 1,000 |
| 10,000 | 8,000 | 1,000 | 700 | 200 | 100 | 10,000 |
| 100,000 | 80,000 | 10,000 | 7,000 | 2,000 | 1,000 | 100,000 |

No records disappeared: for each size, the five outcome counts add up to the input record count.

## Repeated-import behavior

All 9 repeated uploads returned the original reconciliation ID with `idempotentReplay=true`; none launched a second batch job.

| Records | Replay time (individual) | Average | Median | Wrong/new run IDs |
|---:|---|---:|---:|---:|
| 1,000 | 283.74, 292.20, 299.06 ms | 291.67 ms | 292.20 ms | 0 |
| 10,000 | 285.75, 281.10, 278.07 ms | 281.64 ms | 281.10 ms | 0 |
| 100,000 | 500.76, 423.69, 369.82 ms | 431.42 ms | 423.69 ms | 0 |

The replay time still includes streaming the upload to disk and calculating SHA-256 before the database uniqueness check. It is therefore expected to grow with file size even though the batch job is not rerun.

## Interpretation and limitations

- The 100,000-record median was 104.949 seconds at 952.84 records/second. This is a real result, not an extrapolation.
- Throughput improves after the JVM/database warm up and the fixed startup costs are amortized. This suite does not attempt to isolate warm-up effects.
- The processor currently looks up the ledger by transaction reference for each valid row. The roughly 105-second 100k result makes bulk prefetching or a database-side comparison a reasonable future experiment, but no improvement is claimed until it is implemented and remeasured.
- These are sequential batch measurements, not an HTTP concurrency or multi-user load test.
- Docker Desktop shared the host with the operating system and the Codex session. Background host activity was not controlled.
- No percentile latency, maximum-user, or horizontal-scaling claim can be inferred from this suite.

## Reproducing or extending the benchmark

Run `docker compose up --build`, wait for `/actuator/health`, and execute the command above. Change `-Runs` for more samples. Commit only updated measurements you actually produced, together with the date and environment description. If an implementation change is intended to improve performance, benchmark the same commit-independent dataset distribution before and after the change and preserve both raw result files.
