package com.swiftpay.ledger.exception;

public class LedgerConcurrencyException extends RuntimeException{

    public LedgerConcurrencyException(String message) {
        super(message);
    }

    public LedgerConcurrencyException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
