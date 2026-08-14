# Architecture Decision Records

Short records of decisions with real trade-offs, written down when made rather than reconstructed
later. Format: Status / Context / Decision / Rationale / Consequences.

- [0001 — Choreography over orchestration](0001-choreography-over-orchestration.md)
- [0002 — Pessimistic over optimistic locking](0002-pessimistic-over-optimistic-locking.md)
- [0003 — Kafka over RabbitMQ](0003-kafka-over-rabbitmq.md)
- [0004 — Database-per-service, shared instance](0004-database-per-service.md)
- [0005 — Idempotent-consumer pattern](0005-idempotent-consumer-pattern.md)

New ADRs get the next sequential number and stay immutable once accepted — a changed decision gets
a new ADR that supersedes the old one (link both ways) rather than editing history.
