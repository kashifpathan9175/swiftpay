package com.swiftpay.analyticsworker.exception;

public class InvalidPaymentCompletedEventException extends RuntimeException {
    public InvalidPaymentCompletedEventException(String message) {
        super(message);
    }
}
