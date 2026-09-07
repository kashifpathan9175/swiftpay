package com.swiftpay.transactiongateway.service;

import com.swiftpay.transactiongateway.domain.dto.request.PaymentRequest;
import com.swiftpay.transactiongateway.domain.dto.response.PaymentResponse;

public interface PaymentService {

    public PaymentResponse createPayment(
            PaymentRequest request
    );

    public PaymentResponse getPayment(
            String transactionId
    );
}
