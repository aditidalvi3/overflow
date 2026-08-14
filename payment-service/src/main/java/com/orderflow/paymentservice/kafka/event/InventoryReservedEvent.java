package com.orderflow.paymentservice.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryReservedEvent(UUID eventId, UUID correlationId, Long orderId, Instant occurredAt) {
}
