# Architecture

## System Diagram
```
React (seat map, waiting room UI)
        │  HTTP + WebSocket
Spring Boot API
   ├── PostgreSQL   — events, seats, holds, orders, payments (source of truth)
   ├── Redis        — rate limits, waiting-room state, and cache only
   ├── Kafka        — payment-events and notification-events
   └── WebSocket     — broadcast seat status changes to connected clients
```

## System Boundaries
- PostgreSQL is the authoritative record for seats, holds, orders, and payments.
- Redis and WebSockets are disposable performance and delivery layers. Clients treat WebSocket updates as best-effort notifications and reconcile by refetching the REST seat map after reconnecting.
- The waiting room uses a Redis sorted set for FIFO queue order and a separate expiry-scored set for active admissions. Admission tokens are opaque, buyer- and event-scoped Redis values with a short TTL; PostgreSQL ticket ownership remains unchanged by queue admission.
- Kafka carries durable asynchronous domain events. A transactional outbox prevents a database-to-broker dual-write failure.
- The initial deployment is deliberately single-region with one PostgreSQL primary. Multi-region writes, cross-region failover, and globally fair queues are explicit non-goals.

## Component Decisions & Tradeoffs

| Component | Reason chosen | Alternative considered |
|---|---|---|
| PostgreSQL | Transactional guarantees for orders/seats | NoSQL — rejected, strong consistency needed |
| Durable DB holds | Recovery-safe hold expiry and auditable state | Redis-only TTL — fast but loses the authoritative hold record on failure |
| Redis waiting room | Fast queue state, approximate position, rate-limited admission, and short-lived admission tokens | Kafka queue — useful for durable event processing, but awkward for a user-facing queue position |
| Optimistic locking (version column) | Avoids holding DB locks during user think-time | Pessimistic row locks — would block concurrent reads |
| Transactional outbox | Atomically records a domain event with the database transaction before an idempotent publisher sends it to Kafka | Direct publish post-commit — risks message loss |

## Build Stages & Exit Criteria

| Stage | Scope | Done when... |
|---|---|---|
| 1 | Event catalog, auth, static seat map, atomic PostgreSQL reservations | Full reserve flow works end-to-end; concurrent requests cannot sell one seat twice |
| 2 | Seat holds with durable expiry + optimistic locking | Concurrent requests for the same seat → exactly 1 succeeds; expiry is tested and recoverable |
| 3 | Live seat updates + load-test script | WebSocket reflects state across clients; load test shows 0 duplicate confirmations |
| 4 | Waiting room, rate limiting, Kafka payment events, retry/DLQ, audit log | Full flow survives simulated spike; DLQ receives failed messages; audit log complete |

## Reliability and Delivery Rules
- WebSocket notifications do not carry authoritative state. Clients reconnect and refetch seats; the API returns `expiresAt` so a hold countdown can be reconstructed after disconnect.
- Checkout retries provide an `Idempotency-Key` HTTP header. Keys are unique per authenticated user, and a retry with the same key returns the original order outcome.
- Checkout creates a pending order and `PaymentRequested` outbox event in one transaction. An idempotent payment worker consumes the Kafka event, calls the mocked payment provider, and writes the final order and payment result.
- The outbox publisher claims rows safely, retries transient failures, and sends exhausted failures to a DLQ. Consumers are idempotent because at-least-once delivery is expected.
