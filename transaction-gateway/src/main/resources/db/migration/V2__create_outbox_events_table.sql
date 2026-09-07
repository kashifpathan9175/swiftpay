CREATE TABLE transaction_gateway.outbox_events (
                               id UUID PRIMARY KEY,
                               aggregate_type VARCHAR(100) NOT NULL,
                               aggregate_id VARCHAR(100) NOT NULL,
                               event_type VARCHAR(100) NOT NULL,
                               topic VARCHAR(255) NOT NULL,
                               payload JSONB NOT NULL,
                               status VARCHAR(20) NOT NULL,
                               attempts INTEGER NOT NULL DEFAULT 0,
                               available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               published_at TIMESTAMPTZ,

                               CONSTRAINT ck_outbox_events_status
                                   CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),

                               CONSTRAINT ck_outbox_events_attempts
                                   CHECK (attempts >= 0)
);
CREATE INDEX idx_outbox_events_pending
    ON transaction_gateway.outbox_events (status, available_at);

CREATE INDEX idx_outbox_events_aggregate
    ON transaction_gateway.outbox_events (aggregate_type, aggregate_id);

CREATE INDEX idx_outbox_events_created_at
    ON transaction_gateway.outbox_events (created_at);