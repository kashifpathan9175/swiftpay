# SwiftPay — Real-Time Payment Ledger

A resilient, event-driven P2P payment platform built for the SwiftPay hackathon
challenge. See [`docs/DESIGN.md`](docs/DESIGN.md) for the full architecture reasoning
(double-entry ledger, transactional outbox, idempotency, partitioning strategy, etc.)
and [`docs/PROCESS.md`](docs/PROCESS.md) for the AI-assisted development log.

## Architecture

```
                 ┌─────────────────────┐         ┌──────────────────────┐
   Client ──────▶│  Service A: Gateway │         │  Service B: Ledger   │
                  │  POST /v1/payments  │         │  Kafka consumer      │
                  │  Redis idempotency   │         │  Double-entry debit/ │
                  │  Postgres (PENDING)  │         │  credit, atomic tx   │
                  │  Outbox → Kafka       │──────▶ │  GET tx history      │
                  └─────────────────────┘ Kafka:  └──────────────────────┘
                                          payment-initiated
                                          payment-completed
                                          payment-failed
```

- **Service A (Transaction Gateway)** — accepts payment requests, validates against a
  cached (non-authoritative) balance, persists PENDING + an outbox event atomically,
  relays the outbox to Kafka.
- **Service B (Ledger Service)** — consumes `PaymentInitiated`, performs the
  authoritative balance check and double-entry transfer inside one DB transaction,
  emits `PaymentCompleted`/`PaymentFailed`, exposes transaction history.
- **Service C (Analytics)** — out of scope for this submission; see DESIGN.md §11 for
  the reasoning and how it would be approached with more time.

## Stack

Java 21, Spring Boot 3.3, PostgreSQL 16, Apache Kafka, Redis, Flyway, Testcontainers,
Docker Compose, GitHub Actions.

## Running Locally

```bash
docker compose up --build
```

This brings up Postgres (with separate `swiftpay_gateway` / `swiftpay_ledger`
databases), Kafka + Zookeeper, Redis, and both services.

- Service A: http://localhost:8081 — Swagger UI at `/swagger-ui.html`
- Service B: http://localhost:8082 — Swagger UI at `/swagger-ui.html`
- Health checks: `GET /actuator/health` on each service

## Running Tests

```bash
cd service-a-gateway && mvn verify
cd service-b-ledger && mvn verify
```

Integration tests use Testcontainers (real Postgres + Kafka in Docker), not mocks,
for the persistence and messaging layers.

## Project Layout

```
service-a-gateway/   Transaction Gateway (REST API + outbox)
service-b-ledger/    Ledger Service (Kafka consumer + double-entry ledger)
docs/DESIGN.md        Architecture decisions and reasoning
docs/PROCESS.md        AI-assisted development log
infra/                 Supporting infra scripts (multi-DB Postgres init)
.github/workflows/     CI: build, test, Docker image build
```

## What's Deliberately Out of Scope

See `docs/DESIGN.md` §11 for the full list and reasoning — in short: Service C
(ClickHouse analytics), the full 250 TPS / 1M-transaction PCAP load test, and
production-hardened K8s manifests (HPA/NetworkPolicy) were deprioritized in favor of
getting the core payment flow (Services A + B) correct, tested, and well-reasoned
within the submission timeline. A smaller-scale k6 load test is included instead.

## Status

🚧 Work in progress — architecture and scaffolding complete, core payment flow
implementation in progress. This README is updated as the build progresses.
