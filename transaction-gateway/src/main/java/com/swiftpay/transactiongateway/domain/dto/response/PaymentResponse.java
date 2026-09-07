package com.swiftpay.transactiongateway.domain.dto.response;

import com.swiftpay.transactiongateway.domain.entities.Payment;
import com.swiftpay.transactiongateway.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(

        String transactionId,
        Long senderId,
        Long receiverId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentResponse from(Payment payment) {
        if (payment == null) {
            throw new IllegalArgumentException(
                    "payment must not be null"
            );
        }

        return new PaymentResponse(
                payment.getTransactionId(),
                payment.getSenderId(),
                payment.getReceiverId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getFailureReason(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
