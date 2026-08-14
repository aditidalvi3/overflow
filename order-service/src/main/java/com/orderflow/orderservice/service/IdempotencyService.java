package com.orderflow.orderservice.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Order creation is now async (a saga, not a single local transaction), so unlike Phase 1's
 * IdempotencyService this does not cache a full response snapshot - the final order status
 * resolves later as saga events arrive. Instead the Redis value is just the orderId; callers
 * look up the order's CURRENT state from the DB on a replay.
 */
@Service
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<String> findOrderId(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public void saveOrderId(String key, String orderId, Duration ttl) {
        redisTemplate.opsForValue().set(key, orderId, ttl);
    }
}
