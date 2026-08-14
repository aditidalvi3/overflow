package com.orderflow.paymentservice.kafka.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentProcessedEvent(
        UUID eventId,
        UUID correlationId,
        Long orderId,
        Long paymentId,
        Long amountCents,
        String providerRef,
        Instant occurredAt) {
}
