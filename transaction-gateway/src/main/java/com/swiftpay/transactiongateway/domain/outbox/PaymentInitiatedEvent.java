package com.swiftpay.transactiongateway.domain.outbox;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record PaymentInitiatedEvent(
        UUID eventId,
        String transactionId,
        Long senderId,
        Long receiverId,
        BigDecimal amount,
        String currency) {

    public PaymentInitiatedEvent {
        Objects.requireNonNull(
                eventId,
                "eventId must not be null"
        );

        requireText(transactionId, "transactionId");

        requirePositive(senderId, "senderId");
        requirePositive(receiverId, "receiverId");

        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException(
                    "senderId and receiverId must be different"
            );
        }

        Objects.requireNonNull(
                amount,
                "amount must not be null"
        );

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "amount must be greater than zero"
            );
        }

        if (amount.scale() > 4) {
            throw new IllegalArgumentException(
                    "amount must have at most 4 decimal places"
            );
        }

        String normalizedCurrency =
                requireText(currency, "currency").toUpperCase();

        if (normalizedCurrency.length() != 3) {
            throw new IllegalArgumentException(
                    "currency must contain exactly 3 characters"
            );
        }
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    fieldName + " must not be null or blank"
            );
        }

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be null or blank"
            );
        }

        return value.trim();
    }

    private static void requirePositive(
            Long value,
            String fieldName
    ) {
        if (value == null) {
            throw new NullPointerException(
                    fieldName + " must not be null"
            );
        }

        if (value <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be greater than zero"
            );
        }
    }
}