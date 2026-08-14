package com.orderflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PlaceOrderRequest(
        @NotEmpty @Valid List<OrderItemRequest> items,
        String paymentToken
) {
}
