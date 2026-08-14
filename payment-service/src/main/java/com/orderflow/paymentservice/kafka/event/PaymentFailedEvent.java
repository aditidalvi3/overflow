package com.orderflow.paymentservice.kafka.event;

import java.time.Instant;
import java.util.UUID;

/** {@code status} is either {@code FAILED} or {@code TIMEOUT} (see PaymentStatus), sent as a string per the contract. */
public record PaymentFailedEvent(
        UUID eventId,
        UUID correlationId,
        Long orderId,
        String status,
        String reason,
        Instant occurredAt) {
}
