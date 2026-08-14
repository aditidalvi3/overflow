package com.orderflow.orderservice.kafka.event;

public record OrderCreatedItem(Long productId, Integer quantity, Long unitPriceCents) {
}
