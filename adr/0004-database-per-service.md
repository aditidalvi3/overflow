# ADR 0004: Database-per-service, one shared Postgres instance

## Status
Accepted

## Context
Phase 1's monolith had a single Postgres database with one schema shared by every table. Splitting
into order-service, inventory-service, payment-service, and notification-service raises the
question of what "owns" data means now, and how strictly to enforce it.

## Decision
Each service gets its own **database** (`order_db`, `inventory_db`, `payment_db`,
`notification_db`) — no service ever queries another's tables directly, only through that
service's API or the events it publishes. Locally and in `docker-compose.yml`, all four databases
run inside a single shared Postgres **container/instance** (created via an init script), not four
separate Postgres processes.

## Rationale
- **Logical isolation is the part that actually matters for the microservices story.** The
  interview-relevant property is "inventory-service can change its schema without asking
  order-service's permission" and "order-service can't accidentally join across a service
  boundary in a query" — both are fully true with separate databases on a shared instance. Whether
  the bytes live in one `postgres` process or four is an operational/cost concern, not an
  architectural one.
- **Four Postgres containers for local dev is real overhead for no local-dev benefit.** Four
  separate instances would mean four sets of connection pools, four healthchecks, four volumes to
  reason about when debugging locally, with no additional isolation guarantee over four databases
  on one instance — nothing about "one instance, four databases" lets order-service reach into
  inventory-service's tables that separate instances would have prevented; that boundary is
  enforced by "no service has credentials to another's database," not by network/process
  separation.
- **Migrating to fully separate instances later is a deployment-config change, not a code
  change.** Every service already only knows its own `DB_HOST`/`DB_NAME` via env vars — pointing
  `inventory-service` at a different host in production is a docker-compose/Kubernetes manifest
  edit, not an application change.

## Consequences
- Each service's Flyway migrations run against its own database only — there's no cross-service
  migration coordination to worry about, which is the point.
- Sharing a Postgres instance means its resource limits (connections, CPU, disk I/O) are shared
  across all four services' workloads. Fine at this project's scale; a real production
  deployment scaling any one service significantly would be the trigger to give it its own
  instance — which the database-per-service boundary already makes possible without further
  application changes.
- Any query that needs data from two services' domains (e.g. "list a user's orders with product
  names") must be done via API calls or events, never a SQL join — this project accepts the
  n+1-call cost of that (e.g. order-service calling inventory-service's `GET /api/products/{id}`
  to price a cart) as the price of the boundary, rather than reaching for a shared read database.
