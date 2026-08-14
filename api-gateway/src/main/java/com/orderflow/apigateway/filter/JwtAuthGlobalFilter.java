package com.orderflow.apigateway.filter;

import com.orderflow.apigateway.security.JwtValidator;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Edge fast-fail JWT check. If an Authorization: Bearer token is present it must be a
 * well-formed, correctly-signed, unexpired token or the request is rejected with 401
 * before ever reaching a downstream service. Requests with no Authorization header at
 * all are passed through unchanged - each downstream service's own security config
 * decides whether the path requires auth. The gateway never grants authorization by
 * itself; downstream services still perform their own RBAC checks.
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtValidator jwtValidator;

    public JwtAuthGlobalFilter(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || authHeader.isBlank()) {
            return chain.filter(exchange);
        }

        if (!authHeader.startsWith(BEARER_PREFIX)) {
            return reject(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        return jwtValidator.validate(token)
                .map(claims -> chain.filter(exchange))
                .orElseGet(() -> reject(exchange));
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Invalid or expired token\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1; // after RateLimitGlobalFilter
    }
}
