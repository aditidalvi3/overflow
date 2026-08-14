# ADR 0005: `processed_events` table for idempotent Kafka consumption

## Status
Accepted

## Context
Kafka's delivery guarantee is at-least-once: a consumer that crashes after processing a message
but before committing its offset will see that message again on restart; a rebalance can do the
same. Several of this project's consumers have side effects that must not happen twice —
decrementing inventory and charging a (mock) payment being the two that matter most. We need a
way for each consumer to recognize "I've already handled this" and skip redundant work.

Options considered: (a) make the business logic itself naturally idempotent (e.g. decrement is
naturally not idempotent — running it twice oversells); (b) rely on Kafka's exactly-once
semantics (transactional producers/consumers); (c) an explicit dedupe record per
(topic, business key) that the consumer checks before acting.

## Decision
Option (c): every consuming service owns a local `processed_events(topic, order_id)` table with a
unique constraint on `(topic, order_id)`. Inside the same `@Transactional` boundary as the
business logic, the consumer inserts into `processed_events` first; a unique-constraint violation
means this event was already handled, so the handler logs and returns without repeating the side
effect.

## Rationale
- **Business logic doesn't have to be redesigned to tolerate replays.** "Decrement inventory by 2"
  is not naturally idempotent no matter how it's written; wrapping it in a dedupe check is far
  simpler than inventing an idempotent formulation (e.g. "set inventory to exactly X" requires
  knowing X, which itself requires the same kind of bookkeeping).
- **Kafka exactly-once semantics (transactional producers) solve producer-to-broker duplication,
  not consumer-side replay from rebalances/crashes-after-processing-before-commit**, and add
  meaningful complexity (transactional IDs, read-committed isolation, coordinating the
  transaction with the DB write) for a guarantee that's narrower than it sounds. The
  `processed_events` table is a smaller, easier-to-reason-about mechanism that fully covers the
  failure modes this project actually needs to survive.
- **The same DB transaction that does the business logic also records "handled it,"** so there's
  no window where a crash between "recorded as processed" and "actually decremented inventory"
  leaves them inconsistent — they commit or roll back together.

## Consequences
- Every consumer pays one extra `INSERT` (and a unique-constraint check) per event, and every
  service needs its own migration for this table. Judged worth it given what a double-decrement or
  double-charge would cost versus this.
- `processed_events` grows unbounded over time with no eviction in this project — acceptable at
  demo scale; a production version would need a retention/cleanup job (e.g. delete rows older than
  the Kafka topic's retention window, since a message that old can no longer be redelivered
  through normal consumer-group replay).
- The dedupe key is `(topic, order_id)`, not a per-event UUID — this is deliberate: it's not "has
  this exact event been seen" but "has this service already handled this order for this topic,"
  which is the granularity that actually matters for correctness here (each topic only ever
  carries at most one meaningful event per order in this saga's design).
