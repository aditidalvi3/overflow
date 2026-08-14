package com.orderflow.paymentservice.kafka.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        UUID correlationId,
        Long orderId,
        Long userId,
        Long totalCents,
        String paymentToken,
        List<OrderItemPayload> items,
        Instant occurredAt) {

    public record OrderItemPayload(Long productId, Integer quantity, Long unitPriceCents) {
    }
}
