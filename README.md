# Payment Gateway

Production-style payment API — Java 21, Spring Boot 3, hexagonal architecture.

## Quick Start

```bash
docker-compose up -d
./mvnw clean install -DskipTests
./mvnw spring-boot:run -pl gateway-api
```

Swagger UI → http://localhost:8080/swagger-ui.html

## Architecture

```
gateway-api        (REST, Security, OpenAPI)
    ↓ depends on
gateway-core       (domain, services, ports — zero framework imports)
    ↑ implemented by
gateway-persistence (JPA, PostgreSQL)
gateway-batch      (Spring Batch settlement reports)
```

## Submit a payment

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Idempotency-Key: $(uuidgen)" \
  -H "X-Merchant-Id: 00000000-0000-0000-0000-000000000001" \
  -H "Content-Type: application/json" \
  -d '{"amount":250.00,"currency":"EUR","method":"CARD","description":"Order #1"}'
```

## Key design decisions

| Decision | Why |
|---|---|
| BigDecimal for money | float/double cannot represent 0.1 exactly — rounding errors accumulate |
| Idempotency keys | Clients retry on timeout; we must never charge twice |
| Hexagonal architecture | gateway-core has zero Spring/JPA imports — fully unit-testable |
| Java 21 virtual threads | One config line; massively improves I/O throughput |
| ArchUnit tests | Enforce layer boundaries at build time, not just in code reviews |
| Spring Batch | Chunk-oriented settlement reports — same pattern used at SWIFT |
