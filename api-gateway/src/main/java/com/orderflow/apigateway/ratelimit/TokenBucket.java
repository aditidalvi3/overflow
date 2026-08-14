package com.orderflow.apigateway.ratelimit;

/**
 * Fixed-capacity token bucket. O(1) per {@link #tryConsume}: one subtraction, one multiply, one
 * min/max clamp - no scan over request history, unlike a sliding-window log (which is O(n) in
 * the number of requests within the window, or O(log n) with a sorted structure, and grows
 * memory with request volume instead of staying at one {@code double}+one {@code long} per key).
 * The trade-off: a token bucket allows short bursts up to {@code capacity} even right after being
 * fully drained and refilling past a boundary, where a precise sliding-window log would not - an
 * accepted trade for O(1) cost at gateway scale.
 *
 * <p>Not a Spring bean - one instance per rate-limit key, held by {@link TokenBucketRateLimiter}.
 */
final class TokenBucket {

    private final double capacity;
    private final double refillTokensPerNano;

    private double tokens;
    private long lastRefillNanos;

    TokenBucket(double capacity, double refillTokensPerSecond, long nowNanos) {
        this.capacity = capacity;
        this.refillTokensPerNano = refillTokensPerSecond / 1_000_000_000.0;
        this.tokens = capacity;
        this.lastRefillNanos = nowNanos;
    }

    synchronized boolean tryConsume(long nowNanos) {
        refill(nowNanos);
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    private void refill(long nowNanos) {
        long elapsedNanos = nowNanos - lastRefillNanos;
        if (elapsedNanos <= 0) {
            return;
        }
        double refilled = elapsedNanos * refillTokensPerNano;
        tokens = Math.min(capacity, tokens + refilled);
        lastRefillNanos = nowNanos;
    }
}
