package com.orderflow.notificationservice.kafka;

import java.time.Instant;
import java.util.UUID;

public record OrderConfirmedEvent(UUID eventId, UUID correlationId, Long orderId, Long userId, Instant occurredAt) {
}
