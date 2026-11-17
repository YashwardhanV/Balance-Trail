# Benchmark results

## Suggested pairs and gateway formats (2026-11-16, version 0.3.0)

Same machine as below (Linux VM, 2 vCPU, PostgreSQL 16 on the same machine, no added delay). The generator now also writes 1 "typo pair" per 100 rows: the gateway ID has an extra character (`B-0000096X`) and the ledger has the real ID. So per 100,000 rows: 7,000 missing in the ledger, 2,000 missing from the file, and 1,000 expected suggestions. Raw numbers: `benchmark-data/results-20261116-pairs-and-formats.json`.

| 100,000 rows | Run 1 | Run 2 |
|---|---:|---:|
| Standard 4-column file | 10.4 s | 10.7 s |
| Messy 6-column file read through a gateway format (`dd/MM/yyyy` dates) | 11.1 s | 11.0 s |
| Pair suggestion step (7,000 × 2,000 leftover rows) | 0.12 s | 0.13 s |

Every run produced exactly the expected counts, including 1,000 suggested pairs, none ambiguous. Reading through a format costs about 0.4 s per 100,000 rows: picking columns and parsing one date pattern is cheap next to the database work.

**A bug the benchmark caught (in the generator, not the app):** the first run suggested 2,000 pairs, all ambiguous. Amounts repeated every 50,000 rows and accounts every 10,000, so rows 50,000 apart had the same account and amount, and their IDs (`B-0000096X` vs `B-0050096`) were exactly 2 characters apart. Each typo row really did fit two ledger rows, and the app correctly flagged both as ambiguous instead of picking one. The generator now uses `index % 49999` for amounts.

Not measured here: the Gemini call. It only happens when header rules can't map a sample file, once per new gateway layout, and never during a reconciliation run.

## Before and after "one ledger query per chunk" (2026-11-16)

Measured on a Linux VM (2 vCPU, 7 GB RAM) with PostgreSQL 16 on the same machine, one reconciliation at a time, chunk size 100. Both versions ran on the same machine with the same data. Raw numbers: `benchmark-data/results-20261116-before-after.json`.

Per 100 rows the generated file has 80 matches, 10 amount mismatches, 7 rows missing from the ledger, 2 invalid rows and 1 duplicate. The new version also reports 1 ledger row per 100 that the file leaves out (`MISSING_IN_GATEWAY`). Every run produced exactly the expected counts.

### With 1 ms delay between app and database

A small TCP proxy delayed every packet from the app to PostgreSQL by 1 ms. This is roughly what a database across a network, or inside Docker Desktop's VM, costs per query. It is the case that matters, because it is where one query per row hurts.

| Rows | 0.1.0 (1 query per row) | 0.2.0 (1 query per chunk) |
|---:|---:|---:|
| 10,000 | 21.4 s, 21.8 s | 4.7 s, 4.4 s |
| 100,000 | 170.5 s, 174.1 s, 173.5 s | 25.2 s, 23.2 s, 22.5 s |
| **100,000, median** | **173.5 s (~580 rows/s)** | **23.2 s (~4,300 rows/s)** |

Importing a 91,000-row ledger through `POST /ledger-imports` took about 11.5 s under the same delay.

### Without added delay

With PostgreSQL on the same machine, a query costs only a few hundredths of a millisecond, so saving 99,000 of them makes no visible difference: about 10–12 s per 100,000 rows for both versions. At that point the time goes to inserting the results and committing each chunk, not to the lookups.

### Ledger queries

Logging every SQL statement for a 300-row file showed 300 ledger `SELECT`s before and 3 after (one per chunk). PostgreSQL still checks the foreign key from each saved result to the ledger, in both versions; that happens inside the database and costs no round trip.

### What batching broke at first: a stale query plan

The first 100,000-row run after loading a large ledger took **56–59 s** with the new code, and later runs took 10 s. Statement logging showed 200 ledger lookups taking up to 357 ms each. PostgreSQL caches the plan of a prepared query per connection, and the plan for `WHERE transaction_ref IN (...)` had been made while the ledger was small (scan the whole table). It kept that plan until auto-analyze refreshed the statistics. The old `WHERE transaction_ref = ?` always used the primary key, so it never showed this.

Fix: the ledger import job runs `ANALYZE ledger_transaction` when it finishes, which refreshes the statistics and makes PostgreSQL drop the old plans. With it, the same first run took 12–14 s. The PowerShell benchmark loads the ledger with `COPY` (bypassing the import job), so it runs `ANALYZE` itself.

## Earlier results (2026-08-07, version 0.1.0)

Measured on a Windows 11 laptop (Intel Core i5-10200H, 8 GB RAM) with the app and PostgreSQL 17 in Docker Desktop.

| Rows | Runs | Median processing time | Median throughput | Failed runs |
|---:|---:|---:|---:|---:|
| 1,000 | 3 | 2.9 s | ~350 rows/s | 0 |
| 10,000 | 3 | 11.0 s | ~910 rows/s | 0 |
| 100,000 | 3 | 104.9 s | ~950 rows/s | 0 |

Raw numbers: `benchmark-data/results-20260807-090413.json` (its run status `COMPLETED_WITH_SKIPS` is from an older version of the pipeline). Docker Desktop's per-query cost is why these numbers sit between the two tables above.

## Re-running

With `docker compose up --build` healthy, from PowerShell:

```powershell
./scripts/run-benchmarks.ps1 -Sizes 1000,10000,100000 -Runs 3
```

Each run uses its own transaction date, so ledger rows generated for earlier runs are not reported as missing from the current file.
