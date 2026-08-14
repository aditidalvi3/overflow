package com.orderflow.inventoryservice.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryReservationFailedEvent(
        UUID eventId, UUID correlationId, Long orderId, String reason, Instant occurredAt) {
}
