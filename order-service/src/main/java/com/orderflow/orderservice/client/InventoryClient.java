package com.orderflow.orderservice.client;

import com.orderflow.orderservice.dto.ProductDto;
import com.orderflow.orderservice.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Thin synchronous wrapper over inventory-service's product catalog endpoint. Per the contract,
 * this is the one synchronous inter-service call allowed in this phase - it's a read-only price
 * lookup at order-creation time, not part of the saga (stock reservation itself stays async via
 * Kafka).
 */
@Component
public class InventoryClient {

    private final RestClient restClient;

    public InventoryClient(@Value("${orderflow.inventory-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public ProductDto getProduct(Long productId) {
        try {
            return restClient.get()
                    .uri("/api/products/{id}", productId)
                    .retrieve()
                    .body(ProductDto.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new NotFoundException("Product not found: " + productId);
        }
    }
}
