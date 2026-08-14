package com.orderflow.inventoryservice.kafka.event;

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
        List<Item> items,
        Instant occurredAt
) {
    public record Item(Long productId, Integer quantity, Long unitPriceCents) {
    }
}
