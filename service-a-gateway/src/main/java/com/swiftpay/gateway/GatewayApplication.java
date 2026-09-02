package com.swiftpay.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Service A - Transaction Gateway.
 *
 * Responsibilities:
 *  - Accept POST /v1/payments requests
 *  - Enforce idempotency (Redis fast-path + Postgres unique constraint as source of truth)
 *  - Perform an optimistic (non-authoritative) balance pre-check
 *  - Persist the payment request + an outbox event in a single DB transaction
 *  - Relay outbox events to Kafka (see outbox.config / outbox.service, added in a later commit)
 *
 * See docs/DESIGN.md for the reasoning behind each of these.
 */
@SpringBootApplication
@EnableScheduling // outbox relay runs on a fixed-delay poll
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
