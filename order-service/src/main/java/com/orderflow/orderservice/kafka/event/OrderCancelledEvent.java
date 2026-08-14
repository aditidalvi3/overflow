package com.orderflow.orderservice.kafka.event;

import java.time.Instant;

// Published by order-service on POST /api/orders/{id}/cancel. Topic: order.cancelled
public record OrderCancelledEvent(
        String eventId,
        String correlationId,
        Long orderId,
        Instant occurredAt
) {
}
