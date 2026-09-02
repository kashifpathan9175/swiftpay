package com.swiftpay.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Service B - Ledger Service.
 *
 * Responsibilities:
 *  - Consume PaymentInitiated events from Kafka
 *  - Perform the authoritative balance check + atomic double-entry transfer
 *    (DEBIT sender, CREDIT receiver) inside a single DB transaction
 *  - Emit PaymentCompleted or PaymentFailed back to Kafka
 *  - Expose GET /v1/accounts/{accountId}/transactions for history
 *
 * See docs/DESIGN.md for the reasoning behind each of these.
 */
@SpringBootApplication
public class LedgerApplication {
    public static void main(String[] args) {
        SpringApplication.run(LedgerApplication.class, args);
    }
}
