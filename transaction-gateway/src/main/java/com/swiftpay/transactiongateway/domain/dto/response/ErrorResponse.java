package com.swiftpay.transactiongateway.domain.dto.response;

import org.springframework.validation.FieldError;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(

        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldError> fieldErrors
) {
    public ErrorResponse {
        if (timestamp == null) {
            timestamp = Instant.now();
        }

        if (fieldErrors == null) {
            fieldErrors = List.of();
        }
    }

    public record FieldError(
            String field,
            String message
    ) {
    }
}
