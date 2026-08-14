package com.orderflow.orderservice.kafka.event;

import java.time.Instant;

// Consumed by order-service. Topic: inventory.reservation-failed
public record InventoryReservationFailedEvent(
        String eventId,
        String correlationId,
        Long orderId,
        String reason,
        Instant occurredAt
) {
}
