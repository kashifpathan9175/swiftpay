-- Service A (Transaction Gateway) schema
-- Owns: payment request records + transactional outbox
-- Does NOT own the ledger itself (that's Service B) — Service A only records intent.

CREATE TABLE payments (
    transaction_id  UUID PRIMARY KEY,
    sender_id       UUID NOT NULL,
    receiver_id     UUID NOT NULL,
    amount          NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency        CHAR(3) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
        -- PENDING -> COMPLETED | FAILED (updated when Service B's result event is consumed back, if we wire that loop)
    failure_reason  VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_sender ON payments (sender_id);
CREATE INDEX idx_payments_receiver ON payments (receiver_id);
CREATE INDEX idx_payments_status ON payments (status);

-- Transactional Outbox: written in the SAME db transaction as the payments insert.
-- A separate relay process reads unpublished rows and pushes them to Kafka,
-- then marks them published. See docs/DESIGN.md section 3.
CREATE TABLE outbox_events (
    id             BIGSERIAL PRIMARY KEY,
    aggregate_id   UUID NOT NULL,          -- transaction_id
    event_type     VARCHAR(50) NOT NULL,   -- e.g. 'PaymentInitiated'
    kafka_topic    VARCHAR(100) NOT NULL,
    kafka_key      VARCHAR(100) NOT NULL,  -- partition key, e.g. sender account_id
    payload        JSONB NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ             -- NULL = not yet relayed to Kafka
);

-- The relay's core query is "unpublished rows, oldest first" — index for that.
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

-- Minimal account/balance snapshot for the OPTIMISTIC pre-check at ingress only.
-- This is NOT the source of truth for balance — see docs/DESIGN.md section 7.
-- The authoritative check happens in Service B against the double-entry ledger.
CREATE TABLE account_balance_cache (
    account_id      UUID PRIMARY KEY,
    cached_balance  NUMERIC(19,4) NOT NULL,
    currency        CHAR(3) NOT NULL,
    refreshed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
