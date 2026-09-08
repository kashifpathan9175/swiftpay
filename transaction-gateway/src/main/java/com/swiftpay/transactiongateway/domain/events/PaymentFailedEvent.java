package com.swiftpay.transactiongateway.domain.events;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentFailedEvent(

        UUID eventId,
        String transactionId,
        Long senderId,
        Long receiverId,
        BigDecimal amount,
        String currency,
        String reason
) {
}
