package com.orderflow.dto;

public record ProductResponse(Long id, String sku, String name, Long priceCents, Integer quantityAvailable) {
}
