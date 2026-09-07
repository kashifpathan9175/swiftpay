CREATE SCHEMA IF NOT EXISTS swift_pay_ledger;

CREATE TABLE swift_pay_ledger.accounts (
                                           id BIGSERIAL PRIMARY KEY,
                                           user_id BIGINT NOT NULL,
                                           currency VARCHAR(3) NOT NULL,
                                           balance NUMERIC(19,4) NOT NULL CHECK (balance >= 0),
                                           version BIGINT NOT NULL DEFAULT 0,
                                           created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           CONSTRAINT uk_accounts_user_id UNIQUE (user_id)
);

CREATE TABLE swift_pay_ledger.ledger_entries (
                                                 id BIGSERIAL PRIMARY KEY,
                                                 transaction_id VARCHAR(64) NOT NULL,
                                                 account_id BIGINT NOT NULL,
                                                 entry_type VARCHAR(10) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
                                                 amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
                                                 balance_after NUMERIC(19,4) NOT NULL,
                                                 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 CONSTRAINT fk_ledger_entries_accounts
                                                     FOREIGN KEY (account_id)
                                                         REFERENCES swift_pay_ledger.accounts (id)
);

CREATE INDEX idx_ledger_entries_transaction_id
    ON swift_pay_ledger.ledger_entries (transaction_id);

CREATE INDEX idx_ledger_entries_account_id
    ON swift_pay_ledger.ledger_entries (account_id);

CREATE INDEX idx_ledger_entries_created_at
    ON swift_pay_ledger.ledger_entries (created_at DESC);