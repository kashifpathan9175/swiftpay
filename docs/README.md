# SwiftPay documentation

This directory holds project architecture notes, operational guidance, and service design references.

## Modules
- transaction-gateway: ingress and API orchestration layer
- ledger-service: authoritative accounting and event processing layer
- analytics-worker: event-driven consumer and reporting pipeline

## Notes
- Java 21 and Spring Boot 3.x are used throughout the platform.
- Database schema management is intentionally deferred to Flyway migrations.
- No business logic, payment processing, or message handlers are implemented in this initial scaffold.
