# Payment Processing Demo — Java 17

A mock credit card payment processing application built with Spring Boot 3.x (Java 17) and React.

## Quick Start

```bash
cd payment-app-java17
mvn spring-boot:run
```

Then open **http://localhost:8080** in your browser.

---

## What Changed from Java 11

| Area | Java 11 (`payment-app`) | Java 17 (`payment-app-java17`) |
|------|------------------------|-------------------------------|
| Spring Boot | 2.7.18 | 3.2.5 |
| Java version | 11 | 17 |
| `javax.*` imports | `javax.persistence.*`, `javax.validation.*` | `jakarta.persistence.*`, `jakarta.validation.*` |
| `AuthorizeRequest` | Verbose POJO with getters/setters | **Record** |
| `CaptureRequest` | Verbose POJO with getter/setter | **Record** |
| `RefundRequest` | Verbose POJO with getter/setter | **Record** |
| `PaymentResponse` | POJO + Builder pattern | **Record** (canonical constructor) |
| `PaymentService.getHistory()` | `Collectors.toList()` | `Stream.toList()` (unmodifiable) |
| Error messages | String concatenation | **Text blocks** with `.formatted()` |
| Exception handling | Explicit cast after `instanceof` | **Pattern matching** `instanceof` |
| Prometheus property | `management.metrics.export.prometheus.enabled` | `management.prometheus.metrics.export.enabled` |
| Dockerfile build stage | `maven:3.8.8-eclipse-temurin-11` | `maven:3.9.6-eclipse-temurin-17` |
| Dockerfile runtime | `gcr.io/distroless/java11-debian12:nonroot` | `gcr.io/distroless/java17-debian12:nonroot` |

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/payments/authorize` | Authorize a card transaction |
| `POST` | `/api/payments/capture` | Capture an authorized transaction |
| `POST` | `/api/payments/refund` | Refund a captured transaction |
| `GET`  | `/api/payments/{id}` | Get transaction by ID |
| `GET`  | `/api/payments/history` | List recent 20 transactions |
| `POST` | `/admin/cache/clear` | Clear the local Caffeine cache |
| `GET`  | `/actuator/health` | Readiness / health probe |
| `GET`  | `/actuator/prometheus` | Prometheus metrics |

### Authorize a Payment

```bash
curl -X POST http://localhost:8080/api/payments/authorize \
  -H "Content-Type: application/json" \
  -d '{
    "cardNumber": "4263970000005262",
    "expiryMonth": "12",
    "expiryYear": "26",
    "cvv": "123",
    "amount": 99.99,
    "currency": "USD"
  }'
```

### Capture a Transaction

```bash
curl -X POST http://localhost:8080/api/payments/capture \
  -H "Content-Type: application/json" \
  -d '{"transactionId": "<id from authorize response>"}'
```

### Refund a Transaction

```bash
curl -X POST http://localhost:8080/api/payments/refund \
  -H "Content-Type: application/json" \
  -d '{"transactionId": "<id from capture response>"}'
```

---

## Test Card Numbers

| Network    | Card Number       |
|------------|-------------------|
| Visa       | 4263970000005262  |
| MasterCard | 5425230000004415  |
| Amex       | 374101000000608   |

Use any future expiry date (e.g., `12/26`) and any 3–4 digit CVV.

---

## Simulated Behavior

- **Processing delay**: 200–500 ms per request
- **Random decline rate**: ~10% of transactions
- **Response codes**:
  - `00` — Approved
  - `51` — Insufficient Funds
  - `05` — Do Not Honor
  - `54` — Expired Card

---

## Transaction Status Flow

```
AUTHORIZED → CAPTURED → REFUNDED
     └──────→ DECLINED (terminal)
```

---

## Docker

### Build

```bash
docker build -t payment-app-java17 .
```

### Run

```bash
docker run -p 8080:8080 payment-app-java17
```

The image uses a **distroless** base (`gcr.io/distroless/java17-debian12:nonroot`) and runs as a **non-root** user (uid 65532).

---

## Tech Stack

| Layer     | Technology                        |
|-----------|-----------------------------------|
| Backend   | Java 17, Spring Boot 3.2.5        |
| Database  | H2 (in-memory)                    |
| Cache     | Caffeine (local, 10 min TTL)      |
| Metrics   | Micrometer + Prometheus           |
| Frontend  | React 18 (CDN, no build step)     |
| Container | Distroless Java 17, non-root user |

---

## H2 Console

Available at **http://localhost:8080/h2-console**

- JDBC URL: `jdbc:h2:mem:paymentdb`
- Username: `sa`
- Password: *(empty)*

---

## Java 17 Features Applied

### Records (`AuthorizeRequest`, `CaptureRequest`, `RefundRequest`, `PaymentResponse`)
Records auto-generate the canonical constructor, accessors, `equals()`, `hashCode()`, and `toString()`. Jackson 2.15 (Spring Boot 3.x) deserializes records via the canonical constructor. Hibernate Validator 8.x validates annotations on record components.

### Text Blocks (`PaymentService`)
Error messages in `capture()` and `refund()` use text blocks with `.formatted()` for cleaner multi-line string templates.

### Pattern Matching `instanceof` (`PaymentController`)
Exception handlers use `if (e instanceof IllegalStateException ise)` — eliminates the redundant explicit cast.

### `Stream.toList()` (`PaymentService.getHistory()`)
Replaces `Collectors.toList()` with the Java 16+ `Stream.toList()`, which returns an unmodifiable list — safer for a read-only history endpoint.

### `jakarta.*` Namespace
All `javax.persistence.*` and `javax.validation.*` imports replaced with `jakarta.*` as required by Jakarta EE 9+ / Spring Boot 3.x.