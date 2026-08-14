package com.orderflow.orderservice.kafka.event;

import java.time.Instant;

// Consumed by order-service. Topic: payment.failed. `status` is FAILED or TIMEOUT.
public record PaymentFailedEvent(
        String eventId,
        String correlationId,
        Long orderId,
        String status,
        String reason,
        Instant occurredAt
) {
}
