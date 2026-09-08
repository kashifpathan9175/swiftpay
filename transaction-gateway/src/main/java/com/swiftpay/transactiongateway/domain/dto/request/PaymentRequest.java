package com.swiftpay.transactiongateway.domain.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PaymentRequest(

       @JsonProperty("idempotency_key")
       @NotBlank(message = "idempotency_key is required")
       String idempotencyKey,

       @JsonProperty("sender_id")
       @NotNull(message = "sender_id is required")
       @Positive(message = "sender_id must be greater than zero")
       Long senderId,

       @JsonProperty("receiver_id")
       @NotNull(message = "receiver_id is required")
       @Positive(message = "receiver_id must be greater than zero")
       Long receiverId,

       @JsonProperty("amount")
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

       @JsonProperty("currency")
       @NotBlank(message = "currency is required")
       String currency
) {
}
