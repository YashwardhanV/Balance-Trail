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
        bigint version
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

| Object | Purpose |
|---|---|
| `uk_app_user_username` | Prevents duplicate login identities |
| `ck_ledger_amount_positive` | Keeps invalid ledger money values out of the source of truth |
| `uk_run_owner_hash (owner_id, file_sha256)` | Makes an identical upload idempotent per analyst |
| `uk_item_run_line (run_id, line_number)` | Ensures one observable outcome per physical input line |
| `uk_item_run_accepted_gateway` partial unique index | Prevents two accepted results for one gateway ID while allowing duplicate/invalid evidence rows |
| `idx_run_owner_created` | Supports newest-first run history |
| `idx_run_status_created` | Supports lifecycle/operations queries |
| `idx_item_run_status_line` | Supports paginated discrepancies in CSV order |
| `idx_ledger_account_date` | Supports plausible future ledger investigation by account and date |

Spring Batch's `BATCH_*` metadata tables are also stored in PostgreSQL. They belong to the framework's job repository and are intentionally omitted from the business ER diagram.
