-- 1. New outcomes: account and date are now compared, and ledger rows missing
--    from the gateway file are reported too.
ALTER TABLE reconciliation_item DROP CONSTRAINT ck_item_status;
ALTER TABLE reconciliation_item ADD CONSTRAINT ck_item_status CHECK (
    status IN ('MATCHED', 'AMOUNT_MISMATCH', 'ACCOUNT_MISMATCH', 'DATE_MISMATCH',
               'MISSING_IN_LEDGER', 'MISSING_IN_GATEWAY', 'INVALID', 'DUPLICATE')
);

-- A MISSING_IN_GATEWAY row comes from the ledger, so it has no CSV line number.
-- Every other row still must have one.
ALTER TABLE reconciliation_item ALTER COLUMN line_number DROP NOT NULL;
ALTER TABLE reconciliation_item ADD CONSTRAINT ck_item_line_number CHECK (
    line_number IS NOT NULL OR status = 'MISSING_IN_GATEWAY'
);

-- Keep the ledger side of the comparison so an analyst can see both values.
ALTER TABLE reconciliation_item
    ADD COLUMN ledger_account_number VARCHAR(32),
    ADD COLUMN ledger_transaction_date DATE;

-- "Is this ledger row mentioned anywhere in this run?" (MISSING_IN_GATEWAY step)
CREATE INDEX idx_item_run_gateway_id ON reconciliation_item (run_id, gateway_transaction_id);
-- "Ledger rows inside this date range" (MISSING_IN_GATEWAY step)
CREATE INDEX idx_ledger_transaction_date ON ledger_transaction (transaction_date);

ALTER TABLE reconciliation_run
    ADD COLUMN account_mismatch_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN date_mismatch_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN missing_in_gateway_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN ledger_version BIGINT NOT NULL DEFAULT 0;

-- 2. Re-upload rules.
--    Old rule: one run per (user, file). A FAILED run blocked that file forever, and a
--    re-upload after the ledger changed returned stale results.
--    New rule: one non-failed run per (user, file, ledger version).
ALTER TABLE reconciliation_run DROP CONSTRAINT uk_run_owner_hash;
CREATE UNIQUE INDEX uk_run_owner_hash_ledger
    ON reconciliation_run (owner_id, file_sha256, ledger_version)
    WHERE status <> 'FAILED';

-- 3. Ledger imports. Every import gets a new id; the highest id is the current
--    "ledger version" stored on each reconciliation run.
CREATE SEQUENCE ledger_import_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ledger_import (
    id BIGINT PRIMARY KEY DEFAULT nextval('ledger_import_seq'),
    uploaded_by BIGINT NOT NULL REFERENCES app_user(id),
    original_file_name VARCHAR(255) NOT NULL,
    file_sha256 VARCHAR(64) NOT NULL,
    stored_file_path VARCHAR(700) NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_count BIGINT NOT NULL DEFAULT 0,
    imported_count BIGINT NOT NULL DEFAULT 0,
    invalid_count BIGINT NOT NULL DEFAULT 0,
    failure_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    CONSTRAINT ck_ledger_import_status CHECK (
        status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')
    )
);

CREATE INDEX idx_ledger_import_created ON ledger_import (created_at DESC);

-- One row per rejected ledger line, so the analyst can fix the file.
CREATE TABLE ledger_import_error (
    import_id BIGINT NOT NULL REFERENCES ledger_import(id) ON DELETE CASCADE,
    line_number BIGINT NOT NULL,
    transaction_id VARCHAR(64),
    reason VARCHAR(500) NOT NULL,
    PRIMARY KEY (import_id, line_number)
);
