package com.orderflow.apigateway.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketTest {

    @Test
    void allowsBurstUpToCapacity_thenRejects() {
        long t0 = 0L;
        TokenBucket bucket = new TokenBucket(3, 1, t0);

        assertThat(bucket.tryConsume(t0)).isTrue();
        assertThat(bucket.tryConsume(t0)).isTrue();
        assertThat(bucket.tryConsume(t0)).isTrue();
        assertThat(bucket.tryConsume(t0)).isFalse(); // capacity exhausted, no time has passed
    }

    @Test
    void refillsOverTime_atConfiguredRate() {
        long t0 = 0L;
        TokenBucket bucket = new TokenBucket(1, 2, t0); // capacity 1, refills at 2 tokens/sec

        assertThat(bucket.tryConsume(t0)).isTrue();
        assertThat(bucket.tryConsume(t0)).isFalse(); // no time passed yet

        long halfSecondLater = t0 + 500_000_000L; // 0.5s -> 1 token refilled at 2/sec
        assertThat(bucket.tryConsume(halfSecondLater)).isTrue();
        assertThat(bucket.tryConsume(halfSecondLater)).isFalse(); // drained again immediately after
    }

    @Test
    void refillNeverExceedsCapacity() {
        long t0 = 0L;
        TokenBucket bucket = new TokenBucket(2, 100, t0); // fast refill

        long muchLater = t0 + 10_000_000_000L; // 10s at 100 tokens/sec would be way over capacity
        assertThat(bucket.tryConsume(muchLater)).isTrue();
        assertThat(bucket.tryConsume(muchLater)).isTrue();
        assertThat(bucket.tryConsume(muchLater)).isFalse(); // capped at capacity=2, not unlimited
    }
}
