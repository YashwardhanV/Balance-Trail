# Database / ER Diagram

```mermaid
erDiagram
    APP_USER ||--o{ RECONCILIATION_RUN : owns
    RECONCILIATION_RUN ||--o{ RECONCILIATION_ITEM : contains
    LEDGER_TRANSACTION o|--o{ RECONCILIATION_ITEM : compared_with

    APP_USER {
        bigint id PK
        varchar username UK
        varchar password_hash
        varchar role
        boolean enabled
        timestamptz created_at
    }

    LEDGER_TRANSACTION {
        varchar transaction_ref PK
        varchar account_number
        numeric amount "CHECK > 0"
        date transaction_date
        varchar description
    }

    RECONCILIATION_RUN {
        uuid id PK
        bigint owner_id FK
        varchar original_file_name
        varchar file_sha256
        varchar stored_file_path
        varchar status
        bigint total_count
        bigint matched_count
        bigint amount_mismatch_count
        bigint missing_count
        bigint invalid_count
        bigint duplicate_count
        varchar failure_message
        timestamptz created_at
        timestamptz started_at
        timestamptz finished_at
    }

    RECONCILIATION_ITEM {
        bigint id PK
        uuid run_id FK
        bigint line_number
        varchar gateway_transaction_id
        varchar account_number
        numeric gateway_amount
        varchar ledger_transaction_id FK
        numeric ledger_amount
        date transaction_date
        varchar status
        varchar reason
        timestamptz created_at
    }
```

## Constraints and indexes

| Object | Why it exists |
|---|---|
| `uk_app_user_username` | No two users with the same login |
| `ck_ledger_amount_positive` | Ledger amounts must be greater than zero |
| `uk_run_owner_hash (owner_id, file_sha256)` | The same user can't create two runs for the same file |
| `uk_item_run_line (run_id, line_number)` | One result per CSV line |
| `ck_run_status`, `ck_item_status` | Only known status values can be stored |
| `idx_run_owner_created` | Run history: my runs, newest first |
| `idx_item_run_status_line` | Discrepancy page: this run's non-matched rows in line order |

Ledger lookups use the `transaction_ref` primary key, so they need no extra index.

`reconciliation_item` ids come from a sequence that hands out 100 values at a time (`allocationSize = 100`). With an identity column Hibernate would have to insert rows one by one to learn each id; with a sequence it can send the 100 inserts of a chunk as one JDBC batch.

Spring Batch also creates its own `BATCH_*` tables in the same database to record job and step executions. They are left out of the diagram above.
