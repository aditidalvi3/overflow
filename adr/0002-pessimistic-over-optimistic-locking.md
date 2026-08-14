# ADR 0002: Pessimistic locking for inventory decrements

## Status
Accepted

## Context
Concurrent order placement against the same product must never oversell — two customers racing
to buy the last unit must not both succeed. `Inventory.version` (a JPA `@Version` column) is in
place, which would support optimistic locking: read, compute the decrement, write with a
`WHERE version = ?` check, and retry on `OptimisticLockException`. The alternative is pessimistic
locking: `SELECT ... FOR UPDATE` to take a row lock before reading, so no concurrent transaction
can read a stale quantity in the first place.

## Decision
Pessimistic locking (`InventoryRepository.findByProductIdForUpdate`, `PESSIMISTIC_WRITE`) at the
point where inventory-service reserves stock for an order.

## Rationale
- **Inventory contention is the hot path we're explicitly protecting**, not an edge case. Optimistic
  locking is the right default when conflicts are rare and you want to avoid taking locks on the
  common case — but a flash-sale-style scenario (many orders racing for the same low-stock SKU) is
  exactly where optimistic locking's retry loop starts to dominate: every retry re-does the read,
  the price/stock check, and the write, and under high contention throughput degrades as retries
  pile up.
- **Failing fast under a held lock is simpler to reason about than a retry loop.** A pessimistic
  lock either succeeds immediately (uncontended) or blocks briefly behind the holder — bounded,
  predictable latency. An optimistic retry loop needs a retry cap, backoff strategy, and a decision
  about what the caller sees if all retries are exhausted (a 409 the client has to retry itself).
- **It keeps a retryable-in-principle failure from leaking to the client.** With optimistic
  locking, a lost race surfaces as an exception that either the service retries internally (adding
  the complexity above) or the client sees as a transient 409 — for what should be an internal
  concurrency-control concern, not something callers need to handle.

## Consequences
- Rows with contested inventory serialize on the lock for the duration of the reserving
  transaction — under very high concurrency for a single hot SKU, this becomes the throughput
  ceiling for that product (acceptable at this project's scale; would need revisiting — e.g.
  sharding a hot counter, or a queue-based reservation buffer — at a scale this design doesn't
  target).
- `Inventory.version` remains in the schema but isn't exercised by the reservation path. We kept
  it rather than dropping it: it costs nothing, and it's the column a future switch to optimistic
  locking (or a read-only "has this row changed" check elsewhere) would use.
- Lock hold time must stay short — the reservation transaction does nothing beyond the row
  lookups/updates for the order's items. Nothing slow (an external HTTP call, a Kafka publish
  inside the same transaction) should ever be added inside that transaction boundary.
