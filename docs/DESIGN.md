# SwiftPay — Architecture & Design Decisions

This document captures the reasoning behind SwiftPay's design, written before implementation
began. It exists so any engineer (or AI agent) picking up this codebase understands *why*
the system looks the way it does, not just what it does.

## 1. Problem Framing

SwiftPay is not a CRUD app that happens to move money — it's a ledger system. That distinction
drives every decision below. A ledger must be:

- **Auditable** — every balance change must be traceable to an immutable record.
- **Consistent under concurrency** — two transfers touching the same account must never
  corrupt the balance.
- **Recoverable** — a crash mid-transaction must never leave money "in limbo" (created or
  destroyed).

## 2. Data Model: Double-Entry Ledger

We reject a naive `accounts.balance` mutable column. Instead:

```
ledger_entries (
  id              BIGSERIAL PRIMARY KEY,
  transaction_id  UUID NOT NULL,
  account_id      UUID NOT NULL,
  entry_type      VARCHAR(6) NOT NULL,   -- 'DEBIT' | 'CREDIT'
  amount          NUMERIC(19,4) NOT NULL,
  currency        CHAR(3) NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (transaction_id, account_id, entry_type)
)
```

Every completed transfer writes exactly two rows: a DEBIT on the sender, a CREDIT on the
receiver, in the same DB transaction. A user's balance is `SUM(credits) - SUM(debits)`,
either computed live for correctness-critical checks or maintained as a materialized
snapshot (`account_balances` table, updated in the same transaction) for fast reads.

**Why this matters:** balance corruption from concurrent updates becomes structurally
impossible — we're appending immutable rows, not racing on an UPDATE. It also gives us
the audit trail for free, which the brief asks for explicitly.

## 3. The Dual-Write Problem → Transactional Outbox

Service A must (a) persist the payment request and (b) notify Kafka. Doing these as two
independent operations risks a classic distributed-systems failure: DB commits, Kafka
publish fails (or the process dies in between) → an event that should have fired never
does, or fires without a durable record behind it.

**Solution: Transactional Outbox.**

```
outbox_events (
  id            BIGSERIAL PRIMARY KEY,
  aggregate_id  UUID NOT NULL,        -- transaction_id
  event_type    VARCHAR(50) NOT NULL, -- 'PaymentInitiated'
  payload       JSONB NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at  TIMESTAMPTZ           -- null until relayed to Kafka
)
```

The payment row and the outbox row are written in the *same* Postgres transaction. A
separate relay process polls `outbox_events WHERE published_at IS NULL`, publishes to
Kafka, then marks the row published. This guarantees "the event exists if and only if
the DB write committed" — no dual-write race.

For this hackathon scope, the relay is a simple polling publisher (poll every ~200ms,
batch of N). In a production system this would be CDC via Debezium, but a polling
publisher is the honest, explainable choice for a 3-day build and is called out as such.

## 4. Idempotency — Two Layers

- **Fast path:** Redis `SET transaction_id:<id> NX EX 86400`. Sub-millisecond rejection
  of duplicate submissions within the request path.
- **Source of truth:** `UNIQUE` constraint on `transaction_id` in the payments table.

Redis is not durable (eviction, restart, failover) so it cannot be the only guard. If
Redis says "new" but Postgres says "duplicate" (e.g., after a Redis flush), the DB
constraint is what actually prevents double-processing — Redis is purely a latency
optimization, not a correctness mechanism.

## 5. Kafka Topic & Partitioning Strategy

- Topics: `payment-initiated`, `payment-completed`, `payment-failed`
- **Partition key: `account_id` (sender)**, not `transaction_id` and not random/round-robin.

Reasoning: Kafka only guarantees ordering *within* a partition. If two transactions
debiting the same account land on different partitions, a consumer could theoretically
process them out of order relative to each other, which is dangerous for a balance
computation. Keying by account_id guarantees all events touching a given account are
processed in submission order.

(Tradeoff acknowledged: this can create hot partitions for high-volume accounts. Out of
scope to solve for this hackathon, but noted as a known limitation.)

## 6. API Contract: Async by Design

`POST /v1/payments` returns **`202 Accepted`** with `{ transaction_id, status: "PENDING" }`,
not a synchronous final result — because the actual transfer happens asynchronously in
Service B. Callers poll `GET /v1/payments/{id}` for final status. This is a deliberate
contract decision, not an artifact of the architecture — it's documented in the OpenAPI
spec and called out here so it isn't mistaken for an unfinished feature.

## 7. Balance Validation: Optimistic at Ingress, Authoritative at Commit

Service A checks the sender's cached/materialized balance before accepting the request —
this is a fast, optimistic check purely to reject obviously-invalid requests early (better
UX, less wasted Kafka traffic). It is **not** the source of truth.

Service B re-checks the actual balance **inside the same DB transaction** that performs
the debit. If funds are insufficient at that point (e.g., another transfer drained the
account in between), Service B emits `PaymentFailed` and no ledger rows are written.

This two-tier check is the difference between "looks correct in a demo" and "correct
under concurrent load," and is one of the more easily-missed details in this kind of
system.

## 8. Resilience

- **Kafka consumer failures:** retry with exponential backoff (bounded attempts), then
  route to a dead-letter topic (`payment-initiated-dlq`) rather than crash-looping on a
  poison message.
- **Offset commit strategy:** manual ack, committed only *after* the DB transaction
  succeeds. Combined with the `transaction_id` idempotency check, this makes Kafka's
  at-least-once delivery safe to reprocess.
- **DB temporarily down:** consumer retries (see above) rather than failing permanently;
  Kafka retains the message until successfully processed.

## 9. Scalability

- Both services are stateless — horizontally scalable via Kubernetes `Deployment` + HPA.
- Postgres access pooled (HikariCP in-app; PgBouncer noted as the production-scale answer).
- Kafka consumer group sized to partition count (documented as a config, not hardcoded)
  so consumer parallelism scales with partitions.

## 10. Observability

- `transaction_id` propagated as both a Kafka message header and an app log correlation
  ID across all services — this is what makes the "real-time audit log" requirement and
  cross-service debugging actually tractable.
- `/health` (liveness) exposed per service; readiness reflects DB + Kafka connectivity.
- Structured JSON logging.

## 11. What Was Deprioritized (and why)

Given a 3-day timeline against a brief written for "2-day effort" plus scope that includes
a bonus analytics service and a 250 TPS / 1,000,000-transaction load test with a full PCAP
packet capture, the following were consciously deprioritized rather than half-built:

| Deferred | Reason |
|---|---|
| Service C (ClickHouse analytics) | Bonus scope; core payment flow correctness prioritized |
| 250 TPS / 1M-txn load test + PCAP | PCAP is raw packet capture, not a standard load-test output (k6/JMeter report throughput & latency, not packet traces); building dedicated capture tooling for this wasn't a good use of 3 days. A smaller-scale k6 load test with a throughput/latency report is included instead. |
| Full K8s manifests (HPA, NetworkPolicy, etc.) | docker-compose covers "spin up the whole stack" requirement; basic K8s Deployment/Service YAML included as a directional example, not production-hardened |
| SAST / dependency scanning in CI | Noted as a next step; CI covers build + test + image build per the stated requirement |

This is a deliberate scope cut, documented rather than silently made.

## 12. AI-Assisted Development Process

This project was built using AI coding agents as the primary implementation accelerator,
consistent with the role's expectations. See `PROCESS.md` for the specific log of what was
AI-generated, what was reviewed/corrected, and where the human engineer overrode an AI
suggestion (e.g., redirecting an initial mutable-balance design toward double-entry
bookkeeping).
