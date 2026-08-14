package com.orderflow.notificationservice.kafka;

import java.time.Instant;
import java.util.UUID;

public record OrderFailedEvent(UUID eventId, UUID correlationId, Long orderId, Long userId, String reason,
                                Instant occurredAt) {
}
