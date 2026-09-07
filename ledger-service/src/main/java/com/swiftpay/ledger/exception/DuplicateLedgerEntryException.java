package com.swiftpay.ledger.exception;

public class DuplicateLedgerEntryException extends RuntimeException {
    public DuplicateLedgerEntryException(String message) {
        super(message);
    }
}
