# OrderFlow — Phase 1

Core order-processing service: JWT auth with role-based access control, PostgreSQL-backed
Product/Inventory/Order/Payment domain, and idempotent order placement with race-condition-safe
inventory deduction and payment-failure compensation. No Kafka/saga yet (that's Phase 2) — this
is the monolith later phases will decompose.

## Run it

```bash
docker compose up --build
```

This builds the app image (multi-stage Maven build, no local Maven needed) and starts Postgres,
Redis, and the app on `http://localhost:8080`. Flyway migrates the schema on startup.

API docs: `http://localhost:8080/swagger-ui.html`

## Smoke test

```bash
# Register (always CUSTOMER role — self-registration can't grant admin)
curl -s -X POST localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"a@example.com","password":"password123"}'
# -> {"token": "...", "userId": 1, "email": "a@example.com"}

TOKEN=<paste token>

# Products are admin-only to create. Log in as the seeded admin (see "Roles & admin bootstrap").
ADMIN_TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@orderflow.local","password":"ChangeMe123!"}' | jq -r .token)

# Create a product
curl -s -X POST localhost:8080/api/products \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1","name":"Widget","priceCents":1500,"initialQuantity":10}'

# Place an order (idempotent — same Idempotency-Key replays the same order).
# paymentToken is optional: omit/blank -> succeeds, "tok_fail" -> declines, "tok_timeout" -> times out.
curl -s -X POST localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: order-1' \
  -d '{"items":[{"productId":1,"quantity":2}]}'

# Repeat the exact same call -> identical response, inventory not double-decremented
curl -s -X POST localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: order-1' \
  -d '{"items":[{"productId":1,"quantity":2}]}'

# Force a declined payment -> order is created with status PAYMENT_FAILED, stock is released
curl -s -X POST localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: order-2' \
  -d '{"items":[{"productId":1,"quantity":2}],"paymentToken":"tok_fail"}'

# Cancel a PAID order -> restores inventory
curl -s -X POST localhost:8080/api/orders/1/cancel -H "Authorization: Bearer $TOKEN"

# Browse the catalog: paginated, optionally filtered by sku/name substring (case-insensitive)
curl -s "localhost:8080/api/products?name=Widget&page=0&size=10"
```

## Design notes

- **Idempotency**: `POST /api/orders` requires an `Idempotency-Key` header. The response is
  cached in Redis (`idempotency:order:{userId}:{key}`, 24h TTL) so a retried request replays the
  original order instead of creating a duplicate — including a failed-payment response, so a
  retry never re-attempts the charge.
- **Stock safety**: order placement runs in a single `@Transactional` method that locks each
  `Inventory` row with `SELECT ... FOR UPDATE` (`InventoryRepository.findByProductIdForUpdate`)
  before checking/decrementing quantity, preventing overselling under concurrent requests.
  Pessimistic locking was chosen over optimistic (`@Version`) retry loops because inventory
  contention is the hot path we're explicitly protecting — failing fast under a held row lock is
  simpler to reason about than retrying a decrement after a lost `@Version` race, and avoids
  surfacing retryable 409s to the client for what should be an internal concern.
- **Payments**: `MockPaymentGateway` stands in for a real gateway (Stripe in Phase 4). Inventory
  is reserved *before* the charge is attempted; if the charge fails or times out, the same
  transaction releases the reserved stock and persists the order as `PAYMENT_FAILED` alongside a
  `Payment` audit row — a single-transaction preview of the compensating-transaction pattern
  Phase 2's cross-service saga will implement for real. `paymentToken` selects the outcome
  deterministically (`tok_fail`, `tok_timeout`, `tok_random` for weighted-random; anything else,
  including blank, succeeds) so both paths are testable without real randomness.
- **RBAC**: `User.role` (`CUSTOMER` | `ADMIN`) is embedded in the JWT and enforced in
  `SecurityConfig` — only `ADMIN` can create products; anyone authenticated can browse the
  catalog and manage their own orders. Self-registration always creates a `CUSTOMER`; the only
  `ADMIN` account is the one seeded at startup (see below).
- **Admin bootstrap**: `AdminBootstrapRunner` creates one admin user on startup from
  `orderflow.admin.email` / `orderflow.admin.password` (env: `ADMIN_EMAIL` / `ADMIN_PASSWORD`) if
  it doesn't already exist. The defaults in `application.yml`/`docker-compose.yml` are dev-only —
  override both env vars for any non-local deployment.
- **Schema**: managed via Flyway (`src/main/resources/db/migration`), not `ddl-auto`.
- **Catalog reads**: `GET /api/products` is paginated (`page`/`size`/`sort`, default size 20) and
  filterable by `sku`/`name` substring. Both the list and single-product reads are backed by a
  Redis-backed `products` cache (`@Cacheable`, 5 min TTL, JSON-serialized via
  `GenericJackson2JsonRedisSerializer` — configured in `CacheConfig`) so repeat catalog reads skip
  Postgres. Any product write (`POST /api/products`) evicts the whole `products` cache rather than
  targeting individual keys — simpler than tracking per-filter/per-page invalidation, and writes
  are rare relative to reads in a catalog.

## Tests

```bash
mvn test      # unit tests only (Mockito, no external services)
mvn verify    # + Testcontainers integration test (needs Docker)
```

## Not in Phase 1

Kafka/Saga cross-service orchestration, Prometheus/Grafana observability, Kubernetes deployment,
rate limiting — planned for later phases.
