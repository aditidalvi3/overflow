package com.orderflow.orderservice.kafka.event;

import java.time.Instant;

// Consumed by order-service. Topic: payment.processed
public record PaymentProcessedEvent(
        String eventId,
        String correlationId,
        Long orderId,
        Long paymentId,
        Long amountCents,
        String providerRef,
        Instant occurredAt
) {
}
