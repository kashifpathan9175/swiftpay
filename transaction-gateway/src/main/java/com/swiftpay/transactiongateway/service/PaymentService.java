package com.swiftpay.transactiongateway.service;

import com.swiftpay.transactiongateway.domain.dto.request.PaymentRequest;
import com.swiftpay.transactiongateway.domain.dto.response.PaymentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentService {

    public PaymentResponse createPayment(
            PaymentRequest request
    );

    public PaymentResponse getPayment(
            String transactionId
    );

    Page<PaymentResponse> getUserTransactions(
            Long userId,
            Pageable pageable
    );

    void markCompleted(
            String transactionId
    );

    void markFailed(
            String transactionId,
            String reason
    );
}
