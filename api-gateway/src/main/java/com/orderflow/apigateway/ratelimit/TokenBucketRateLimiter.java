package com.orderflow.apigateway.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * One {@link TokenBucket} per rate-limit key (client IP by default - see
 * {@link com.orderflow.apigateway.filter.RateLimitGlobalFilter}), created lazily on first use.
 * {@link ConcurrentHashMap#computeIfAbsent} makes bucket creation thread-safe without a global
 * lock; each bucket then serializes its own refill/consume internally.
 */
@Component
public class TokenBucketRateLimiter {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final double capacity;
    private final double refillTokensPerSecond;

    public TokenBucketRateLimiter(
            @Value("${orderflow.rate-limit.capacity:20}") double capacity,
            @Value("${orderflow.rate-limit.refill-per-second:10}") double refillTokensPerSecond) {
        this.capacity = capacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
    }

    public boolean tryConsume(String key) {
        long now = System.nanoTime();
        return buckets.computeIfAbsent(key, k -> new TokenBucket(capacity, refillTokensPerSecond, now))
                .tryConsume(now);
    }
}
