CREATE SEQUENCE app_user_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE reconciliation_item_seq START WITH 1 INCREMENT BY 100;

CREATE TABLE app_user (
    id BIGINT PRIMARY KEY DEFAULT nextval('app_user_seq'),
    username VARCHAR(80) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_app_user_username UNIQUE (username),
    CONSTRAINT ck_app_user_role CHECK (role IN ('ANALYST'))
);

CREATE TABLE ledger_transaction (
    transaction_ref VARCHAR(64) PRIMARY KEY,
    account_number VARCHAR(32) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    transaction_date DATE NOT NULL,
    description VARCHAR(160),
    CONSTRAINT ck_ledger_amount_positive CHECK (amount > 0)
);

CREATE TABLE reconciliation_run (
    id UUID PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES app_user(id),
    original_file_name VARCHAR(255) NOT NULL,
    file_sha256 VARCHAR(64) NOT NULL,
    stored_file_path VARCHAR(700) NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_count BIGINT NOT NULL DEFAULT 0,
    matched_count BIGINT NOT NULL DEFAULT 0,
    amount_mismatch_count BIGINT NOT NULL DEFAULT 0,
    missing_count BIGINT NOT NULL DEFAULT 0,
    invalid_count BIGINT NOT NULL DEFAULT 0,
    duplicate_count BIGINT NOT NULL DEFAULT 0,
    failure_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    CONSTRAINT uk_run_owner_hash UNIQUE (owner_id, file_sha256),
    CONSTRAINT ck_run_status CHECK (
        status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')
    )
);

-- Run history page: "my runs, newest first".
CREATE INDEX idx_run_owner_created ON reconciliation_run (owner_id, created_at DESC);

CREATE TABLE reconciliation_item (
    id BIGINT PRIMARY KEY DEFAULT nextval('reconciliation_item_seq'),
    run_id UUID NOT NULL REFERENCES reconciliation_run(id) ON DELETE CASCADE,
    line_number BIGINT NOT NULL,
    gateway_transaction_id VARCHAR(64),
    account_number VARCHAR(32),
    gateway_amount NUMERIC(19,2),
    ledger_transaction_id VARCHAR(64) REFERENCES ledger_transaction(transaction_ref),
    ledger_amount NUMERIC(19,2),
    transaction_date DATE,
    status VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_item_run_line UNIQUE (run_id, line_number),
    CONSTRAINT ck_item_status CHECK (
        status IN ('MATCHED', 'AMOUNT_MISMATCH', 'MISSING_IN_LEDGER', 'INVALID', 'DUPLICATE')
    )
);

-- Discrepancy page: "items of this run with these statuses, ordered by line".
CREATE INDEX idx_item_run_status_line ON reconciliation_item (run_id, status, line_number);
