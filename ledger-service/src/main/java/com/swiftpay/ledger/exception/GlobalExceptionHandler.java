package com.swiftpay.ledger.exception;

import com.swiftpay.ledger.domain.dto.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(
            AccountNotFoundException ex
    ) {

        log.warn(
                "Account not found: {}",
                ex.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        ErrorResponse.of(
                                HttpStatus.NOT_FOUND.value(),
                                "ACCOUNT_NOT_FOUND",
                                ex.getMessage()
                        )
                );
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(
            InsufficientBalanceException ex
    ) {

        log.warn(
                "Insufficient balance: {}",
                ex.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(
                        ErrorResponse.of(
                                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                                "INSUFFICIENT_BALANCE",
                                ex.getMessage()
                        )
                );
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ErrorResponse> handleCurrencyMismatch(
            CurrencyMismatchException ex
    ) {

        log.warn(
                "Currency mismatch: {}",
                ex.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(
                        ErrorResponse.of(
                                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                                "CURRENCY_MISMATCH",
                                ex.getMessage()
                        )
                );
    }

    @ExceptionHandler(InvalidPaymentEventException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPaymentEvent(
            InvalidPaymentEventException ex
    ) {

        log.warn(
                "Invalid payment event: {}",
                ex.getMessage()
        );

        return ResponseEntity
                .badRequest()
                .body(
                        ErrorResponse.of(
                                HttpStatus.BAD_REQUEST.value(),
                                "INVALID_PAYMENT_EVENT",
                                ex.getMessage()
                        )
                );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception ex
    ) {

        log.error(
                "Unexpected application error",
                ex
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ErrorResponse.of(
                                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                "INTERNAL_SERVER_ERROR",
                                "An unexpected error occurred"
                        )
                );
    }
}
