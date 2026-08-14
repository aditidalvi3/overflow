package com.orderflow.orderservice.dto;

import com.orderflow.orderservice.entity.OrderStatus;

import java.time.Instant;
import java.util.List;

// No paymentStatus/paymentFailureReason here (unlike Phase 1's OrderResponse) - order-service no
// longer owns Payment; a PAYMENT_FAILED status plus the order.failed event's `reason` (logged /
// surfaced by notification-service) is all this service needs to expose.
public record OrderResponse(
        Long id,
        OrderStatus status,
        Long totalCents,
        Instant createdAt,
        List<OrderItemResponse> items
) {
}
