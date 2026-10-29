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
4. `OutboxPoller` (every 5 s, batches of 30) publishes unpublished events to the `payment-events` Kafka topic, keyed by `customerId`, waiting for broker ack (`acks=all`, idempotent producer, 15 s send timeout). On a timeout it stops the batch and retries on the next cycle.

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
| Java 21 virtual threads | One config line; massively improves I/O throughput |
| ArchUnit tests | Enforce layer boundaries at build time, not just in code reviews |
| Spring Batch (WIP) | Chunk-oriented settlement reports; the item reader is not implemented yet |
