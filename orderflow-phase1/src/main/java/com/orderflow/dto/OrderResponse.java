package com.orderflow.dto;

import com.orderflow.entity.OrderStatus;
import com.orderflow.entity.PaymentStatus;

import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        OrderStatus status,
        Long totalCents,
        Instant createdAt,
        List<OrderItemResponse> items,
        PaymentStatus paymentStatus,
        String paymentFailureReason
) {
}
