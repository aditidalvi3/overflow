# inventory-service

Owns `products`/`inventory`/`inventory_reservations` (`inventory_db`). Verifies JWTs issued by
order-service (shared secret) but never issues them itself. See the
[root README](../README.md) for the full saga sequence and [adr/](../adr/) for why.

## API

- `POST /api/products` (`ADMIN` role required) — create a product + initial stock.
- `GET /api/products` (public) — paginated (`page`/`size`/`sort`), filterable by `sku`/`name`
  substring, Redis-cached.
- `GET /api/products/{id}` (public) — Redis-cached; also called synchronously by order-service to
  price carts, so its response shape (`{id, sku, name, priceCents, quantityAvailable}`) is a
  cross-service contract, not just a REST convenience.

Swagger UI: `http://localhost:8082/swagger-ui.html`.

## Saga participation

- `order.created` → reserve stock for every item (all-or-nothing across the whole order) →
  `inventory.reserved` or `inventory.reservation-failed`.
- `payment.failed` / `order.cancelled` → release whatever was reserved for that order (tracked in
  `inventory_reservations`) → `inventory.released`.

## Key env vars

| Var | Default | Purpose |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Saga event bus |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Product-catalog cache |
| `JWT_SECRET` | Phase 1's dev default | Must match order-service (the token issuer) |

## Run its tests standalone

```bash
mvn -B test
```
