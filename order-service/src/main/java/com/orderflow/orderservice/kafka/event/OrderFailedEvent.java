package com.orderflow.orderservice.kafka.event;

import java.time.Instant;

// Published by order-service. Topic: order.failed
public record OrderFailedEvent(
        String eventId,
        String correlationId,
        Long orderId,
        Long userId,
        String reason,
        Instant occurredAt
) {
}
