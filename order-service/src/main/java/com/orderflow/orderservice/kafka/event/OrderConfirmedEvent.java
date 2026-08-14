package com.orderflow.orderservice.kafka.event;

import java.time.Instant;

// Published by order-service. Topic: order.confirmed
public record OrderConfirmedEvent(
        String eventId,
        String correlationId,
        Long orderId,
        Long userId,
        Instant occurredAt
) {
}
