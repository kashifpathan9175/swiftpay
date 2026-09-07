CREATE SCHEMA IF NOT EXISTS transaction_gateway;

CREATE TABLE transaction_gateway.payments (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(64) NOT NULL,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payments_transaction_id UNIQUE (transaction_id),
    CONSTRAINT ck_payments_sender_receiver CHECK (sender_id <> receiver_id)
);

CREATE INDEX idx_payments_sender_created_at ON transaction_gateway.payments (sender_id, created_at DESC);
CREATE INDEX idx_payments_receiver_created_at ON transaction_gateway.payments (receiver_id, created_at DESC);
CREATE INDEX idx_payments_status_created_at ON transaction_gateway.payments (status, created_at DESC);
