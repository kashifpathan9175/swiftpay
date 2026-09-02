-- Service B (Ledger) schema
-- Owns: the authoritative double-entry ledger and derived account balances.
-- This is the source of truth for "how much money does account X have".

CREATE TABLE accounts (
    account_id  UUID PRIMARY KEY,
    currency    CHAR(3) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Immutable double-entry rows. Never UPDATEd, only INSERTed.
-- A completed transfer writes exactly one DEBIT row (sender) and one CREDIT row
-- (receiver), in the same DB transaction. See docs/DESIGN.md section 2.
CREATE TABLE ledger_entries (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  UUID NOT NULL,
    account_id      UUID NOT NULL REFERENCES accounts(account_id),
    entry_type      VARCHAR(6) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount          NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency        CHAR(3) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Prevents the same transaction from writing the same entry type twice for the
    -- same account — a structural idempotency guard at the ledger level, independent
    -- of the gateway's own idempotency check.
    UNIQUE (transaction_id, account_id, entry_type)
);

CREATE INDEX idx_ledger_entries_account ON ledger_entries (account_id, created_at);
CREATE INDEX idx_ledger_entries_transaction ON ledger_entries (transaction_id);

-- Materialized balance snapshot, maintained in the SAME transaction as the
-- ledger_entries insert. Exists purely so reads (balance checks, GET history
-- summaries) don't require summing the whole ledger every time. ledger_entries
-- remains the source of truth; this table is a derived, reconstructible cache.
CREATE TABLE account_balances (
    account_id  UUID PRIMARY KEY REFERENCES accounts(account_id),
    balance     NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency    CHAR(3) NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Tracks processed transaction_ids for Kafka consumer idempotency (at-least-once
-- delivery means we may see the same PaymentInitiated event more than once).
CREATE TABLE processed_events (
    transaction_id  UUID PRIMARY KEY,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    result          VARCHAR(20) NOT NULL  -- 'COMPLETED' | 'FAILED'
);
