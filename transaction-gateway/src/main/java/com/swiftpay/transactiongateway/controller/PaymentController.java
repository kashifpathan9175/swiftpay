package com.swiftpay.transactiongateway.controller;

import com.swiftpay.transactiongateway.domain.dto.request.PaymentRequest;
import com.swiftpay.transactiongateway.domain.dto.response.PaymentResponse;
import com.swiftpay.transactiongateway.exception.InvalidPaymentException;
import com.swiftpay.transactiongateway.service.impl.PaymentServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/payments")
@Tag(   name = "Payments",
        description = "SwiftPay payment APIs")
public class PaymentController {

    private final PaymentServiceImpl paymentService;

    public PaymentController(
            PaymentServiceImpl paymentService
    ) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @Operation(
            summary = "Create payment",
            description = "Creates a PENDING payment and schedules it for ledger processing"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Payment created"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid payment request"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Duplicate payment currently being processed"
            )
    })
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody PaymentRequest request
    ) {
        PaymentResponse response =
                paymentService.createPayment(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{transactionId}")
    @Operation(
            summary = "Get payment",
            description = "Returns the current status of a payment"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Payment found"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Payment not found"
            )
    })
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable("transactionId") String transactionId
    ) {
        return ResponseEntity.ok(
                paymentService.getPayment(transactionId)
        );
    }

    @GetMapping("/users/{userId}/transactions")
    @Operation(
            summary = "Get user transaction history",
            description = "Returns payment transactions where the user is the sender or receiver, newest first"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transaction history returned"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid userId"
            )
    })
    public ResponseEntity<Page<PaymentResponse>> getUserTransactions(
            @Parameter(description = "User ID for the transaction history")
            @PathVariable("userId") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (userId == null || userId <= 0) {
            throw new InvalidPaymentException(
                    "userId must be greater than zero"
            );
        }
        if (page < 0 || size <= 0) {
            throw new InvalidPaymentException(
                    "page must be greater than or equal to zero and size must be greater than zero"
            );
        }

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                paymentService.getUserTransactions(userId, pageable)
        );
    }
}
