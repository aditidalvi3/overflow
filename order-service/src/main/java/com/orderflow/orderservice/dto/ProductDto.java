package com.orderflow.orderservice.dto;

// Response shape of inventory-service's GET /api/products/{id}.
public record ProductDto(Long id, String sku, String name, Long priceCents, Integer quantityAvailable) {
}
