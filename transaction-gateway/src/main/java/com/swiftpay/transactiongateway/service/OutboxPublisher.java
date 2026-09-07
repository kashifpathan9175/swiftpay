package com.swiftpay.transactiongateway.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

public interface OutboxPublisher {


    @Scheduled(
            fixedDelayString = "${swiftpay.outbox.poll-delay-ms:1000}"
    )
    @Transactional
    void publishPendingEvents();
}
