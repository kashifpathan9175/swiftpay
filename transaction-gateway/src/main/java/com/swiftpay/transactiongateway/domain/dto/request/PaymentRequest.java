package com.swiftpay.transactiongateway.domain.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PaymentRequest(


        @NotNull(message = "senderId is required")
        @Positive(message = "senderId must be greater than zero")
        Long senderId,

        @NotNull(message = "receiverId is required")
        @Positive(message = "receiverId must be greater than zero")
        Long receiverId,

        @NotNull(message = "amount is required")
        @DecimalMin(
                value = "0.0001",
                inclusive = true,
                message = "amount must be greater than zero"
        )
        @Digits(
                integer = 15,
                fraction = 4,
                message = "amount must have at most 15 integer digits and 4 decimal places"
        )
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        String currency
) {
}
