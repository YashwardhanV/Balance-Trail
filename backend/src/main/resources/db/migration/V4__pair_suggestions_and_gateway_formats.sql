-- 1. Suggested pairs: "this MISSING_IN_LEDGER row and this MISSING_IN_GATEWAY row are probably
--    the same payment with a typo in the transaction ID". An analyst confirms or rejects each one.
--    The original results are never changed; the decision is recorded next to them.
CREATE TABLE match_suggestion (
    id BIGSERIAL PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES reconciliation_run(id) ON DELETE CASCADE,
    gateway_item_id BIGINT NOT NULL REFERENCES reconciliation_item(id) ON DELETE CASCADE,
    ledger_item_id BIGINT NOT NULL REFERENCES reconciliation_item(id) ON DELETE CASCADE,
    id_distance INT NOT NULL,
    ambiguous BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL,
    decided_by BIGINT REFERENCES app_user(id),
    decided_at TIMESTAMPTZ,
    decision_note VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_suggestion_pair UNIQUE (gateway_item_id, ledger_item_id),
    CONSTRAINT ck_suggestion_status CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED')),
    CONSTRAINT ck_suggestion_distance CHECK (id_distance >= 0)
);

CREATE INDEX idx_suggestion_run ON match_suggestion (run_id, id);

-- A row can be part of at most one confirmed pair, even if two requests confirm at once.
CREATE UNIQUE INDEX uk_suggestion_confirmed_gateway_item
    ON match_suggestion (gateway_item_id) WHERE status = 'CONFIRMED';
CREATE UNIQUE INDEX uk_suggestion_confirmed_ledger_item
    ON match_suggestion (ledger_item_id) WHERE status = 'CONFIRMED';

ALTER TABLE reconciliation_run ADD COLUMN suggested_pair_count BIGINT NOT NULL DEFAULT 0;

-- 2. Gateway formats: a saved column mapping for one gateway's export, e.g. "Txn Ref" is the
--    transaction ID and "Settlement Dt" is the date in dd/MM/yyyy. Formats are never edited:
--    a corrected mapping is saved as a new format, so old runs keep meaning what they meant.
CREATE TABLE gateway_format (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    headers_json VARCHAR(4000) NOT NULL,
    transaction_id_column VARCHAR(100) NOT NULL,
    account_number_column VARCHAR(100) NOT NULL,
    amount_column VARCHAR(100) NOT NULL,
    transaction_date_column VARCHAR(100) NOT NULL,
    date_format VARCHAR(30) NOT NULL,
    suggested_by VARCHAR(20) NOT NULL,
    created_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_gateway_format_suggested_by CHECK (suggested_by IN ('GEMINI', 'HEADER_RULES', 'MANUAL'))
);

CREATE UNIQUE INDEX uk_gateway_format_name ON gateway_format (lower(name));

-- 3. A run now also depends on how the file was read (which format), so the format is part of
--    the "same upload again?" key. 0 stands for the standard 4-column layout.
ALTER TABLE reconciliation_run ADD COLUMN gateway_format_id BIGINT REFERENCES gateway_format(id);
DROP INDEX uk_run_owner_hash_ledger;
CREATE UNIQUE INDEX uk_run_owner_hash_ledger_format
    ON reconciliation_run (owner_id, file_sha256, ledger_version, COALESCE(gateway_format_id, 0))
    WHERE status <> 'FAILED';
