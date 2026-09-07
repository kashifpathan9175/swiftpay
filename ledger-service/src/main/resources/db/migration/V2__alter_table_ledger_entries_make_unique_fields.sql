ALTER TABLE swift_pay_ledger.ledger_entries
    ADD CONSTRAINT uk_ledger_entries_transaction_account
        UNIQUE (transaction_id, account_id);


