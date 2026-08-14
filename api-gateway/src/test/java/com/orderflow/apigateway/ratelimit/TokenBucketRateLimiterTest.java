package com.orderflow.apigateway.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketRateLimiterTest {

    @Test
    void exhaustsCapacity_thenRejects_forSameKey() {
        // Tiny refill rate so the real time elapsed during the test can't refill a token.
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, 0.0001);

        assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
        assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
        assertThat(limiter.tryConsume("1.2.3.4")).isFalse();
    }

    @Test
    void tracksSeparateBuckets_perKey() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 0.0001);

        assertThat(limiter.tryConsume("client-a")).isTrue();
        assertThat(limiter.tryConsume("client-a")).isFalse();
        assertThat(limiter.tryConsume("client-b")).isTrue(); // independent bucket, unaffected by client-a
    }
}
