package com.swiftpay.ledger.domain.dto.response;

import java.time.Instant;

public record ErrorResponse(

        Instant timestamp,
        int status,
        String code,
        String message
) {

    public static ErrorResponse of(
            int status,
            String code,
            String message
    ) {

        return new ErrorResponse(
                Instant.now(),
                status,
                code,
                message
        );
    }
}
