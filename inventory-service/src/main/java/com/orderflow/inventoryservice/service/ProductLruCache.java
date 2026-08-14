package com.orderflow.inventoryservice.service;

import com.orderflow.inventoryservice.dto.ProductResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * In-process L1 cache for single-product reads, sitting in front of {@link ProductService}'s
 * Redis-backed (L2) {@code @Cacheable} lookup - a hit here skips both the Redis round-trip and
 * the DB. Deliberately a plain component rather than a {@code @Cacheable} method on
 * {@link ProductService} itself: Spring's caching annotations only intercept calls that go
 * through the proxy, so a same-class call from one {@code ProductService} method to another
 * wouldn't actually apply the annotation (self-invocation) - keeping this as a separate bean that
 * {@link com.orderflow.inventoryservice.controller.ProductController} calls before
 * {@code ProductService} sidesteps that entirely.
 *
 * <p>Scoped to single-product-by-id lookups only, not the paginated list endpoint - caching
 * arbitrary filter/page combinations in a small bounded LRU has a much larger effective key
 * space for comparatively little hit-rate benefit, whereas "the same handful of hot product ids
 * get read constantly" is exactly the access pattern an LRU is good at.
 */
@Component
public class ProductLruCache {

    private final LruCache<Long, ProductResponse> cache;

    public ProductLruCache(@Value("${orderflow.cache.lru.capacity:200}") int capacity) {
        this.cache = new LruCache<>(capacity);
    }

    public synchronized Optional<ProductResponse> get(Long productId) {
        return cache.get(productId);
    }

    public synchronized void put(Long productId, ProductResponse response) {
        cache.put(productId, response);
    }

    public synchronized void clear() {
        cache.clear();
    }

    public synchronized int size() {
        return cache.size();
    }
}
