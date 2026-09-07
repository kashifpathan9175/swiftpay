package com.swiftpay.ledger.exception;

public class InvalidPaymentEventException extends RuntimeException {
    public InvalidPaymentEventException(String message) {
        super(message);
    }
}
