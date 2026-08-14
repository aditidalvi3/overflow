package com.orderflow.orderservice.dto;

public record OrderItemResponse(Long productId, Integer quantity, Long unitPriceCents) {
}
