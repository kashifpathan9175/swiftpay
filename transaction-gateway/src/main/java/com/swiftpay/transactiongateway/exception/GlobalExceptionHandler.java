package com.swiftpay.transactiongateway.exception;

import com.swiftpay.transactiongateway.domain.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<ErrorResponse.FieldError> errors =
                ex.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(error ->
                                new ErrorResponse.FieldError(
                                        error.getField(),
                                        error.getDefaultMessage()
                                )
                        )
                        .toList();

        ErrorResponse response =
                new ErrorResponse(
                        Instant.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        "VALIDATION_ERROR",
                        "Request validation failed",
                        request.getRequestURI(),
                        errors
                );

        return ResponseEntity
                .badRequest()
                .body(response);
    }

    @ExceptionHandler(DuplicatePaymentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(
            DuplicatePaymentException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        Instant.now(),
                        HttpStatus.CONFLICT.value(),
                        "DUPLICATE_PAYMENT",
                        ex.getMessage(),
                        request.getRequestURI(),
                        List.of()
                ));
    }

    @ExceptionHandler(InvalidPaymentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPayment(
            InvalidPaymentException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        Instant.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        "INVALID_PAYMENT",
                        ex.getMessage(),
                        request.getRequestURI(),
                        List.of()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error(
                "Unhandled exception while processing request path={}",
                request.getRequestURI(),
                ex
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        Instant.now(),
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "INTERNAL_ERROR",
                        "An unexpected error occurred",
                        request.getRequestURI(),
                        List.of()
                ));
    }
}
