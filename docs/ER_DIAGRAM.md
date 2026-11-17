# Database / ER Diagram

```mermaid
erDiagram
    APP_USER ||--o{ RECONCILIATION_RUN : owns
    APP_USER ||--o{ LEDGER_IMPORT : uploads
    RECONCILIATION_RUN ||--o{ RECONCILIATION_ITEM : contains
    LEDGER_TRANSACTION o|--o{ RECONCILIATION_ITEM : compared_with
    LEDGER_IMPORT ||--o{ LEDGER_IMPORT_ERROR : rejects
    RECONCILIATION_RUN ||--o{ MATCH_SUGGESTION : suggests
    RECONCILIATION_ITEM ||--o{ MATCH_SUGGESTION : "gateway side / ledger side"
    GATEWAY_FORMAT o|--o{ RECONCILIATION_RUN : reads
    APP_USER ||--o{ GATEWAY_FORMAT : creates

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
        bigint account_mismatch_count
        bigint date_mismatch_count
        bigint missing_in_gateway_count
        bigint suggested_pair_count
        bigint gateway_format_id FK "null = standard layout"
        bigint ledger_version "id of the ledger import used"
        varchar failure_message
        timestamptz created_at
        timestamptz started_at
        timestamptz finished_at
    }

    RECONCILIATION_ITEM {
        bigint id PK
        uuid run_id FK
        bigint line_number "null only for MISSING_IN_GATEWAY"
        varchar gateway_transaction_id
        varchar account_number
        numeric gateway_amount
        date transaction_date
        varchar ledger_transaction_id FK
        varchar ledger_account_number
        numeric ledger_amount
        date ledger_transaction_date
        varchar status
        varchar reason
        timestamptz created_at
    }

    LEDGER_IMPORT {
        bigint id PK "also the ledger version"
        bigint uploaded_by FK
        varchar original_file_name
        varchar file_sha256
        varchar stored_file_path
        varchar status
        bigint total_count
        bigint imported_count
        bigint invalid_count
        varchar failure_message
        timestamptz created_at
        timestamptz started_at
        timestamptz finished_at
    }

    MATCH_SUGGESTION {
        bigint id PK
        uuid run_id FK
        bigint gateway_item_id FK "the MISSING_IN_LEDGER row"
        bigint ledger_item_id FK "the MISSING_IN_GATEWAY row"
        int id_distance
        boolean ambiguous
        varchar status "PENDING, CONFIRMED, REJECTED"
        bigint decided_by FK
        timestamptz decided_at
        varchar decision_note
        timestamptz created_at
    }

    GATEWAY_FORMAT {
        bigint id PK
        varchar name "unique, case-insensitive"
        varchar headers_json
        varchar transaction_id_column
        varchar account_number_column
        varchar amount_column
        varchar transaction_date_column
        varchar date_format "from an allow-list"
        varchar suggested_by "GEMINI, HEADER_RULES, MANUAL"
        bigint created_by FK
        timestamptz created_at
    }

    LEDGER_IMPORT_ERROR {
        bigint import_id PK, FK
        bigint line_number PK
        varchar transaction_id
        varchar reason
    }
```

## Constraints and indexes

| Object | Why it exists |
|---|---|
| `uk_app_user_username` | No two users with the same login |
| `ck_ledger_amount_positive` | Ledger amounts must be greater than zero |
| `uk_run_owner_hash_ledger_format (owner_id, file_sha256, ledger_version, COALESCE(gateway_format_id, 0)) WHERE status <> 'FAILED'` | One live run per user, file, ledger version and format; failed runs don't block a retry |
| `uk_suggestion_pair (gateway_item_id, ledger_item_id)` | A pair is suggested once; makes a re-run of step 3 a no-op |
| `uk_suggestion_confirmed_gateway_item`, `uk_suggestion_confirmed_ledger_item` (partial, `WHERE status = 'CONFIRMED'`) | A row can be in at most one confirmed pair, even with two requests at once |
| `uk_gateway_format_name (lower(name))` | Format names are unique, ignoring case |
| `uk_item_run_line (run_id, line_number)` | One result per CSV line |
| `ck_item_line_number` | Only `MISSING_IN_GATEWAY` rows may have no line number |
| `ck_run_status`, `ck_item_status` | Only known status values can be stored |
| `idx_run_owner_created` | Run history: my runs, newest first |
| `idx_item_run_status_line` | Discrepancy page: this run's non-matched rows in line order |
| `idx_item_run_gateway_id` | Step 2: "does this run mention this ledger ID?" |
| `idx_ledger_transaction_date` | Step 2: "ledger rows inside the file's date range" |
| `idx_ledger_import_created` | Ledger import list, newest first |
| `ledger_import_error` primary key `(import_id, line_number)` | One reason per rejected line; makes a retried chunk's insert a no-op |

Ledger lookups by ID use the `transaction_ref` primary key, so they need no extra index.

`reconciliation_item` ids come from a sequence that hands out 100 values at a time (`allocationSize = 100`). Step 2's `INSERT ... SELECT` uses the column's `DEFAULT nextval(...)` on the same sequence, so its rows can never get an id Hibernate is also using. With an identity column Hibernate would have to insert rows one by one to learn each id; with a sequence it can send the 100 inserts of a chunk as one JDBC batch.

Spring Batch also creates its own `BATCH_*` tables in the same database to record job and step executions. They are left out of the diagram above.
