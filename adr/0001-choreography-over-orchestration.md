# ADR 0001: Choreography over orchestration for the order saga

## Status
Accepted

## Context
Placing an order touches three independently-owned pieces of state: inventory (reserve stock),
payment (charge the customer), and the order record itself, each now living in its own service
after the Phase 2 split. A single local `@Transactional` method (Phase 1's approach) no longer
works once these are separate services with separate databases — we need a saga.

Two standard shapes exist:
- **Orchestration**: a central coordinator (e.g. an `order-saga-orchestrator` service) explicitly
  calls each participant in sequence and issues compensating calls on failure.
- **Choreography**: each service reacts to events from the others and publishes its own; there is
  no central coordinator, the "flow" emerges from the sum of each service's local reactions.

## Decision
Choreography, via Kafka topics (`order.created`, `inventory.reserved`,
`inventory.reservation-failed`, `payment.processed`, `payment.failed`, `inventory.released`,
`order.confirmed`, `order.failed`, `order.cancelled`).

## Rationale
- **No new service to own.** Orchestration needs a coordinator service with its own deployment,
  on-call ownership, and state machine persistence. For a 4-service saga with a linear happy path
  and one compensating step, that's disproportionate infrastructure.
- **Loose coupling matches the service boundaries we just created.** inventory-service and
  payment-service don't need to know about each other or about an orchestrator's API — they only
  need to agree on event shapes on a topic. Adding a 5th saga step later (e.g. a fraud-check
  service) means that service subscribing to `order.created`, not editing a central coordinator.
- **Kafka is already required** for the async event backbone regardless of which saga shape we
  pick, so choreography doesn't add infrastructure orchestration would also need.

## Consequences
- **The overall flow isn't visible in one place.** Understanding "what happens when payment
  fails" means reading inventory-service's `payment.failed` consumer, not one orchestrator method.
  We mitigate this by keeping topic/event names and the flow diagram in the root README as the
  source of truth for the saga shape, and by making the correlation ID (threaded through every
  event and into logs) the tool for tracing one order across services after the fact.
- **Cyclic-looking dependencies are easy to introduce by accident** (service A reacts to B which
  reacts to A) since nothing enforces a DAG. We avoid this here because the saga is a straight
  line with one compensating branch, but this is the main reason we'd reach for orchestration if
  the saga grew a lot more branching logic (e.g. multiple possible compensation paths, retries
  with backoff across steps, or a human-in-the-loop approval step).
- **Debugging a stuck saga requires correlating across services' logs/DBs** rather than querying
  one orchestrator's state table. `processed_events` tables per service plus correlation-ID
  logging are the substitute for that visibility.
