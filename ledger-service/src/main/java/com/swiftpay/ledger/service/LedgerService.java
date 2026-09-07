package com.swiftpay.ledger.service;

import com.swiftpay.ledger.domain.event.PaymentInitiatedEvent;

public interface LedgerService {

    void processPayment(PaymentInitiatedEvent event);

}
