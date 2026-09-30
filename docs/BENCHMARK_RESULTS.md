# Benchmark results

Measured on 2026-08-07 on a Windows 11 laptop (Intel Core i5-10200H, 8 GB RAM) with the app and PostgreSQL 17 running in Docker Desktop. One reconciliation at a time, chunk size 100.

Each generated file has, per 100 rows: 80 matches, 10 amount mismatches, 7 missing from the ledger, 2 invalid rows and 1 duplicate.

| Rows | Runs | Median processing time | Median throughput | Failed runs |
|---:|---:|---:|---:|---:|
| 1,000 | 3 | 2.9 s | ~350 rows/s | 0 |
| 10,000 | 3 | 11.0 s | ~910 rows/s | 0 |
| 100,000 | 3 | 104.9 s | ~950 rows/s | 0 |

Processing time is the gap between the run's `startedAt` and `finishedAt`. Every run produced exactly the expected counts (for 100,000 rows: 80,000 / 10,000 / 7,000 / 2,000 / 1,000), and uploading each file a second time returned the original run instead of starting a new job.

Raw numbers: `benchmark-data/results-20260807-090413.json`.

**Note:** these numbers were measured before the batch pipeline was simplified (invalid and duplicate rows are now saved by the normal writer instead of one at a time). The run status in the raw file is the old `COMPLETED_WITH_SKIPS`. Re-run the benchmark to get numbers for the current code.

## Re-running

With `docker compose up --build` healthy, from PowerShell:

```powershell
./scripts/run-benchmarks.ps1 -Sizes 1000,10000,100000 -Runs 3
```

The slowest part is the ledger lookup, one query per row. Loading the ledger rows for a whole chunk with one query is the obvious next experiment.
