package com.orderflow.inventoryservice.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryReleasedEvent(UUID eventId, UUID correlationId, Long orderId, Instant occurredAt) {
}
