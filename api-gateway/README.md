# api-gateway

The only externally-exposed service. Spring Cloud Gateway (reactive), no database, no state
beyond the in-memory rate-limit buckets. See the [root README](../README.md) for the overall
architecture and [adr/](../adr/) for why.

## What it does, in filter order

1. **Rate limiting** (`RateLimitGlobalFilter`) — a hand-rolled token-bucket, one bucket per client
   IP, O(1) per request. Over limit → `429` with a `Retry-After` header. See
   `TokenBucket`'s Javadoc for the complexity trade-off vs. a sliding-window log.
2. **JWT edge validation** (`JwtAuthGlobalFilter`) — if a request has an `Authorization: Bearer`
   header, it must be a validly-signed, unexpired token or the request is rejected with `401`
   before reaching a backend. No header at all → passed through; each downstream service's own
   security config decides whether that path needs auth. The gateway never grants authorization
   itself.
3. **Circuit breaking** (Resilience4j, `spring-cloud-starter-circuitbreaker-reactor-resilience4j`)
   — each route has its own named circuit breaker + a 5s time limit; an open breaker or timeout
   routes to `FallbackController`, which returns a stable `503` instead of a hung/confusing error.
4. **Routing** — `/api/auth/**,/api/orders/**` → order-service, `/api/products/**` →
   inventory-service.

## Key env vars

| Var | Default | Purpose |
|---|---|---|
| `ORDER_SERVICE_HOST` / `INVENTORY_SERVICE_HOST` | `localhost` | Route targets |
| `JWT_SECRET` | Phase 1's dev default | Must match order-service (the token issuer) |
| `RATE_LIMIT_CAPACITY` / `RATE_LIMIT_REFILL_PER_SECOND` | `20` / `10` | Token bucket sizing, per client IP |

## Run its tests standalone

```bash
mvn -B test
```
