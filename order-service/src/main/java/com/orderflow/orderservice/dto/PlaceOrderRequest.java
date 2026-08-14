package com.orderflow.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

// paymentToken is optional client-supplied data that order-service does not interpret itself -
// it is only passed through unchanged on the order.created event for payment-service to use,
// since order-service no longer owns Payment.
public record PlaceOrderRequest(
        @NotEmpty @Valid List<OrderItemRequest> items,
        String paymentToken
) {
}
