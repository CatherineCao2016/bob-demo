# Security Scan Report — payment-app-java17

**Scan Date:** 2026-03-01  
**Scope:** All source files under `payment-app-java17/src/` plus `application.properties`, `pom.xml`, `Dockerfile`

---

## Phase 1 — Hardcoded Secrets, API Keys, Passwords & Tokens

| # | File | Line | Severity | Finding | Fix Recommendation |
|---|------|------|----------|---------|-------------------|
| 1 | `src/main/resources/application.properties` | 7–8 | 🟡 MEDIUM | H2 datasource uses default credentials: `username=sa`, `password=` (empty) — trivially guessable for any exposed H2 console. | Set a non-default username and a strong password via environment variables: `spring.datasource.username=${DB_USER}` / `spring.datasource.password=${DB_PASS}`. |
| 2 | `src/main/java/com/demo/payment/service/PaymentService.java` | 29–31 | 🟡 MEDIUM | Real-looking PAN (Primary Account Number) card numbers hardcoded in source as "test cards" (`4263970000005262`, `5425230000004415`, `374101000000608`). | Move test card data to a test-only configuration file or test fixtures; never embed card numbers in production source code. |
| 3 | `src/main/resources/static/index.html` | 242–244 | 🟡 MEDIUM | Same PAN values hardcoded in the production-served frontend JavaScript. | Remove from production HTML; serve test card hints only in a dev/test profile or a separate non-production UI. |

---

## Phase 2 — Logic Flaws, Insecure Patterns & Missing Validation

| # | File | Line | Severity | Finding | Fix Recommendation |
|---|------|------|----------|---------|-------------------|
| 4 | `src/main/resources/application.properties` | 11–12 | 🔴 HIGH | H2 web console enabled (`spring.h2.console.enabled=true`) with no authentication or access restriction — exposes full database access to anyone who can reach the server. | Disable in production: `spring.h2.console.enabled=false`; if needed for dev, restrict via Spring Security or a profile guard (`@Profile("dev")`). |
| 5 | `src/main/java/com/demo/payment/controller/PaymentController.java` | 16 | 🔴 HIGH | `@CrossOrigin(origins = "*")` allows any origin to make cross-origin requests to all endpoints, including the unauthenticated `/admin/cache/clear`. | Restrict to known origins: `@CrossOrigin(origins = "${app.allowed-origins}")`; never use wildcard on APIs that mutate state. |
| 6 | `src/main/java/com/demo/payment/controller/PaymentController.java` | 90–94 | 🔴 HIGH | `/admin/cache/clear` endpoint has no authentication or authorization — any unauthenticated caller can flush the cache, enabling cache-poisoning or DoS. | Protect with Spring Security: require `ROLE_ADMIN` via `@PreAuthorize("hasRole('ADMIN')")` or a security filter chain rule. |
| 7 | `src/main/java/com/demo/payment/service/PaymentService.java` | 44 | 🔴 HIGH | Raw (unmasked) card number (`cardNumber`) is persisted to the database in the `Transaction` entity. Storing full PANs violates PCI-DSS requirements. | Do not persist the raw PAN; store only the masked value (`maskedCardNumber`) and a tokenized reference if needed. |
| 8 | `src/main/java/com/demo/payment/service/PaymentService.java` | 99 | 🟡 MEDIUM | Authorization code is logged at INFO level: `log.info("Transaction {} authorized with auth code {}", ...)`. Auth codes are sensitive payment credentials. | Log only the transaction ID; omit the authorization code from log output. |
| 9 | `src/main/java/com/demo/payment/model/AuthorizeRequest.java` | 26–28 | 🟡 MEDIUM | CVV is accepted and bound as a plain `String` field. Although it is not persisted, there is no explicit check that it is discarded immediately after use, and it may appear in debug logs or serialized error responses. | Mark the CVV field `@JsonProperty(access = Access.WRITE_ONLY)`, ensure it is never logged, and confirm it is not included in any response object. |
| 10 | `src/main/java/com/demo/payment/service/PaymentService.java` | 209–211 | 🟡 MEDIUM | `generateAuthCode()` uses `ThreadLocalRandom`, which is not cryptographically secure. A 6-digit numeric auth code generated this way is predictable under certain conditions. | Replace with `SecureRandom`: `String.format("%06d", new SecureRandom().nextInt(1_000_000))`. |
| 11 | `src/main/java/com/demo/payment/controller/PaymentController.java` | 34, 51, 68, 81 | 🟡 MEDIUM | Internal exception messages (`e.getMessage()`) are returned directly in HTTP 500 responses, potentially leaking stack details, class names, or internal state to clients. | Return a generic error message to the client (e.g., `"An internal error occurred"`) and log the full exception server-side only. |
| 12 | `src/main/resources/application.properties` | 15 | 🟡 MEDIUM | Actuator exposes the `prometheus` endpoint publicly with no authentication. Prometheus metrics can reveal internal application topology, JVM details, and request patterns. | Restrict actuator endpoints to a management-only port or require authentication: `management.endpoint.prometheus.access=restricted`. |
| 13 | `src/main/resources/application.properties` | 16 | 🟠 LOW | `management.endpoint.health.show-details=always` exposes full health details (datasource status, disk space, etc.) to unauthenticated callers. | Set to `when-authorized` so details are only shown to authenticated users. |
| 14 | `src/main/java/com/demo/payment/model/AuthorizeRequest.java` | 23 | 🟠 LOW | Expiry year regex `\\d{2,4}` accepts 3-digit years (e.g., `202`), which would pass validation but produce incorrect expiry logic in `isCardExpired()`. | Tighten to `\\d{2}|\\d{4}` to accept only 2-digit or 4-digit years. |
| 15 | `src/main/resources/static/index.html` | 8–10 | 🟠 LOW | React, ReactDOM, and Babel are loaded from `unpkg.com` CDN without Subresource Integrity (SRI) hashes. A compromised CDN could inject malicious scripts. | Add `integrity="sha384-..."` and `crossorigin="anonymous"` attributes to each `<script>` tag, or self-host the assets. |
| 16 | `src/main/resources/static/index.html` | 349–357 | 🟠 LOW | CVV input uses `type="text"` instead of `type="password"`, displaying the CVV in plaintext on screen. | Change to `type="password"` to mask the CVV value in the browser UI. |
| 17 | `src/main/java/com/demo/payment/service/PaymentService.java` | 171–178 | 🟠 LOW | `simulateProcessingDelay()` uses `Thread.sleep()` on a request-handling thread, which can exhaust the thread pool under load and enable a trivial DoS via concurrent requests. | Remove artificial delays from production code; use a feature flag or profile to enable them only in dev/test environments. |

---

## Summary

| Severity | Count |
|----------|-------|
| 🔴 HIGH   | 4     |
| 🟡 MEDIUM | 7     |
| 🟠 LOW    | 6     |
| **Total** | **17** |

### Top Priorities

1. **Disable the H2 console** in production (Finding #4) — direct database access with no auth.
2. **Remove raw PAN persistence** (Finding #7) — storing full card numbers is a PCI-DSS violation.
3. **Secure the `/admin/cache/clear` endpoint** (Finding #6) — unauthenticated admin action.
4. **Restrict CORS** from wildcard to known origins (Finding #5).
5. **Replace `ThreadLocalRandom` with `SecureRandom`** for auth code generation (Finding #10).