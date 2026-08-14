package com.orderflow.dto;

public record OrderItemResponse(Long productId, Integer quantity, Long unitPriceCents) {
}
