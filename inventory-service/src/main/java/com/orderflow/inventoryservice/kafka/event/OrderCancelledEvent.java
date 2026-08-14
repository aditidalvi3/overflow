package com.orderflow.inventoryservice.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record OrderCancelledEvent(UUID eventId, UUID correlationId, Long orderId, Instant occurredAt) {
}
