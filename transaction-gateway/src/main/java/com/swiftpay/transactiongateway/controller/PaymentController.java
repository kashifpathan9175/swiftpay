package com.swiftpay.transactiongateway.controller;

import com.swiftpay.transactiongateway.domain.dto.request.PaymentRequest;
import com.swiftpay.transactiongateway.domain.dto.response.PaymentResponse;
import com.swiftpay.transactiongateway.service.impl.PaymentServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(   name = "Payments",
        description = "SwiftPay payment APIs")
public class PaymentController {

    private final PaymentServiceImpl paymentService;

    public PaymentController(
            PaymentServiceImpl paymentService
    ) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create/payment")
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
}
