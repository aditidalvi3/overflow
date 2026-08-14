package com.orderflow.inventoryservice.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID eventId, UUID correlationId, Long orderId, String status, String reason, Instant occurredAt) {
}
