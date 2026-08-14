package com.orderflow.orderservice.kafka.event;

import java.time.Instant;
import java.util.List;

// Published by order-service. Topic: order.created
public record OrderCreatedEvent(
        String eventId,
        String correlationId,
        Long orderId,
        Long userId,
        Long totalCents,
        String paymentToken,
        List<OrderCreatedItem> items,
        Instant occurredAt
) {
}
