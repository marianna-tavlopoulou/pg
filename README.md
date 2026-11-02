# Payment Gateway

Production-style payment API — Java 21, Spring Boot 3.2, hexagonal architecture, transactional outbox to Kafka.

## Quick Start

```bash
docker-compose up -d                      # PostgreSQL 16, Redis 7, Kafka 3.7 (KRaft)
./mvnw clean install -DskipTests
./mvnw spring-boot:run -pl gateway-api
```

Swagger UI → http://localhost:8080/swagger-ui.html (OpenAPI JSON at `/api-docs`)

`JWT_SECRET` and `AUTH_API_KEY` are read from the environment; both have local-dev defaults (`AUTH_API_KEY=local-dev-only`).

## Architecture

```
gateway-api          (REST, JWT security, OpenAPI — the runnable Spring Boot app)
    ↓ depends on
gateway-core         (domain model, services, ports)
    ↑ implemented by
gateway-persistence  (JPA + Flyway on PostgreSQL, Redis velocity checks)
gateway-messaging    (outbox poller + Kafka event publisher)

gateway-batch        (Spring Batch settlement report — work in progress, not wired into the app)
```

### Payment flow

1. `POST /api/v1/payments` saves a `PENDING` order. The `Idempotency-Key` is unique in the DB; a repeated key returns the original order.
2. Fraud scoring (amount tiers, high-value wallet payments, Redis-backed velocity and duplicate-amount checks over a 5-minute window) decides the outcome:
   - score < 40 → `COMPLETED` (201)
   - 40 ≤ score < 70 → `PROCESSING`, i.e. held for review (201)
   - score ≥ 70 → `DECLINED` (422)
3. The status update and an `outbox_events` row are written in one transaction. If finalization fails, the client gets the `PENDING` order and should poll `GET /api/v1/payments/{id}`.
4. `OutboxPoller` (every 5 s, batches of 30) publishes unpublished events to the `payment-events` Kafka topic, keyed by `customerId`, waiting for broker ack (`acks=all`, idempotent producer, 15 s send timeout). Delivery is at-least-once and ordered per customer; consumers dedupe on `eventId`. Failure handling is described below.

### Outbox failure handling

| Failure | What happens |
|---|---|
| Kafka timeout or thread interrupt | Batch stops, retried next cycle. Not counted as an attempt (a DLT timeout happens after a failed main publish, which has already counted one). |
| Other publish failure (poison event) | `attempts` +1, `last_error` = exception class + message (max 500 chars). That customer's later events wait (order is kept). |
| `attempts` ≥ `max-attempts` (5) | Event is sent to `payment-events-dlt` (header `dlt-reason`) and marked `dead_lettered`; the customer is unblocked. |
| DLT publish also fails, `attempts` ≥ `max-attempts` + `max-dead-letter-attempts` (5 + 3) | Event is **parked** (`parked_at` set) and kept in the DB with its payload; the customer is unblocked. Log token: `OUTBOX_EVENT_PARKED`. |
| DB failure while recording / marking | Batch stops, retried next cycle. |

Find events that need manual action:

```sql
SELECT id, customer_id, event_type, attempts, last_error, parked_at
FROM outbox_events WHERE parked_at IS NOT NULL ORDER BY parked_at;
```

Config (`gateway.poller.outbox.*`): `max-attempts` (default 5), `max-dead-letter-attempts` (default 3).

There is no un-park / replay tool yet: fix the cause, then re-publish manually. A replay path with human approval and an audit trail is a planned follow-up.

## Submit a payment

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"apiKey":"local-dev-only"}' | jq -r .token)

curl -X POST http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "Content-Type: application/json" \
  -d '{"customerId":"'"$(uuidgen)"'","amount":250.00,"currency":"EUR","method":"CARD","description":"Order #1"}'
```

The merchant is taken from the JWT (currently a stub merchant `00000000-0000-0000-0000-000000000001`). Supported currencies: `USD`, `EUR`, `GBP`. Methods: `CARD`, `BANK_TRANSFER`, `WALLET`.

## Tests

```bash
./mvnw test      # unit tests (Surefire)
./mvnw verify    # + integration tests (*IT, Failsafe) — needs Docker for Testcontainers
```

Integration tests start their own PostgreSQL and Redis via Testcontainers and cover idempotency races, optimistic locking, fraud detection, and payment/outbox atomicity (happy path and rollback). Kafka and the outbox poller are disabled in API integration tests; the messaging module has its own unit tests. CI (GitHub Actions) runs `./mvnw clean verify` on pushes to `main`/`develop` and PRs to `main`.

## Key design decisions

| Decision | Why |
|---|---|
| BigDecimal for money | float/double cannot represent 0.1 exactly — rounding errors accumulate |
| Idempotency keys | Clients retry on timeout; we must never charge twice |
| Hexagonal architecture | gateway-core talks to the outside world only through ports (`PaymentRepository`, `OutboxRepository`, `VelocityCheckPort`, `EventPublisher`); it uses Spring only for DI and `@Transactional`, no JPA/web/Kafka |
| Transactional outbox | Status change and event are committed atomically; Kafka publishing happens after commit, so no event is lost or emitted for a rolled-back payment |
| Optimistic locking | Concurrent updates to the same order fail instead of silently overwriting |
| Retry → DLT → park in DB | A poison event can't block its customer forever; the DB is the dead-letter store of last resort, so nothing is lost even if Kafka rejects the record on both topics |
| Timeouts/interrupts don't count as attempts | They are infrastructure or shutdown problems, not bad events |
| Unblock the customer after dead-lettering/parking | Trade-off: later events may overtake the failed one, so per-customer consumers (e.g. velocity checks) can under-count until it is handled |
| Java 21 virtual threads | One config line; blocking I/O no longer ties up a platform thread, so many more concurrent requests fit |
| ArchUnit tests | Enforce layer boundaries at build time, not just in code reviews |
| Spring Batch (WIP) | Chunk-oriented settlement reports; the item reader is not implemented yet |
