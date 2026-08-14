package com.orderflow.apigateway.filter;

import com.orderflow.apigateway.ratelimit.TokenBucketRateLimiter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Runs before {@link JwtAuthGlobalFilter} - rejecting an over-limit caller should cost as little
 * work as possible, so this happens before JWT parsing/validation. Keyed by client IP: every
 * request costs a token, including unauthenticated ones (register/login are exactly the
 * endpoints most worth protecting from brute-forcing). Behind a real reverse proxy this key
 * should come from a trusted X-Forwarded-For, not the raw socket address - noted rather than
 * implemented here since this gateway isn't deployed behind one.
 */
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private final TokenBucketRateLimiter rateLimiter;

    public RateLimitGlobalFilter(TokenBucketRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String key = clientKey(exchange);
        if (rateLimiter.tryConsume(key)) {
            return chain.filter(exchange);
        }
        return reject(exchange);
    }

    private String clientKey(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null && remoteAddress.getAddress() != null
                ? remoteAddress.getAddress().getHostAddress()
                : "unknown";
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().add("Retry-After", "1");
        String body = "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded, retry shortly\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
