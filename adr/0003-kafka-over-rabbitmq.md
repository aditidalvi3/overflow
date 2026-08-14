# ADR 0003: Kafka over RabbitMQ for the saga event backbone

## Status
Accepted

## Context
The choreography saga (ADR 0001) needs an async message backbone connecting order-service,
inventory-service, payment-service, and notification-service. The two most common choices for
this in the Java/Spring ecosystem are Kafka (log-based streaming) and RabbitMQ (traditional AMQP
broker with per-queue routing).

## Decision
Kafka.

## Rationale
- **Per-order ordering is load-bearing for this saga**, and Kafka gives it for free: producing
  every event for an order with `key = orderId` guarantees all of that order's events land on the
  same partition and are consumed in publish order. RabbitMQ can approximate this (single active
  consumer per queue, or consistent-hash exchanges) but it's not the default shape of the broker —
  you're working against the model rather than with it.
- **Replay is a first-class capability, not a bolt-on.** Kafka retains events on disk for a
  configurable retention window; a consumer group can be reset to an earlier offset and replay
  history — genuinely useful for this project (e.g. bring up a new read model, or reprocess after
  fixing a bug in a consumer) and for debugging (`kafka-console-consumer` from the earliest offset
  to see exactly what happened for one order). RabbitMQ queues are consume-once by design; once a
  message is acked it's gone.
- **It's the tool most likely to be asked about.** Kafka is the de facto standard for event-driven
  microservices and saga backbones in the current Java job market; demonstrating fluency with
  consumer groups, partitioning, and offset semantics is more directly relevant than the
  equivalent RabbitMQ concepts for the roles this project is built to speak to.

## Consequences
- **We take on the idempotent-consumer burden.** Kafka is at-least-once by default (no
  broker-side "don't redeliver this" beyond consumer-committed offsets), so every consumer needs
  its own dedupe guard — the `processed_events` table pattern used in every service. RabbitMQ with
  manual ack has similar redelivery semantics on requeue, so this isn't unique to Kafka, but it's
  worth naming as a cost we deliberately took on rather than got for free.
- **Operationally heavier for a project this size.** A single-node KRaft broker (no Zookeeper) is
  used in `docker-compose.yml` specifically to keep local dev light — a "real" Kafka deployment
  (replicated brokers, a schema registry, monitoring for consumer lag) is out of scope here and
  would be the next investment if this went further.
- **No routing-key-style fan-out semantics.** Each event type is its own topic with every
  interested service subscribing to that whole topic — there's no RabbitMQ-style selective
  routing-key binding. This is a fine trade for this saga's topology (a handful of topics, known
  consumers) and keeps the topic-per-event-type contract easy to reason about.
