# order-service

Owns `users`/`orders`/`order_items` (`order_db`) and JWT issuance (`/api/auth/register`,
`/api/auth/login` — always issues `CUSTOMER`; the one `ADMIN` account is seeded on startup by
`AdminBootstrapRunner` from `ADMIN_EMAIL`/`ADMIN_PASSWORD`). It's the saga's entry point — see the
[root README](../README.md) for the full sequence diagram and [adr/](../adr/) for why it's shaped
this way.

## API

- `POST /api/orders` (auth required, `Idempotency-Key` header required) — prices the cart via a
  synchronous call to inventory-service, persists the order as `PENDING`, publishes
  `order.created`, returns **202 Accepted**. Poll `GET /api/orders/{id}` for the resolved status.
- `GET /api/orders`, `GET /api/orders/{id}` — owner-scoped.
- `POST /api/orders/{id}/cancel` — only for `PAID` orders; publishes `order.cancelled` so
  inventory-service releases the reserved stock.

Swagger UI: `http://localhost:8081/swagger-ui.html`.

## Key env vars

| Var | Default | Purpose |
|---|---|---|
| `INVENTORY_SERVICE_URL` | `http://localhost:8082` | Synchronous product-pricing lookup |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Saga event bus |
| `JWT_SECRET` | Phase 1's dev default | Must match every other service |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@orderflow.local` / `ChangeMe123!` | Seeded admin — override for anything beyond local dev |

## Run its tests standalone

```bash
mvn -B test
```
