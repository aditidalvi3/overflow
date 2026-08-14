package com.orderflow.apigateway.filter;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * Runs before every other filter (including RateLimitGlobalFilter/JwtAuthGlobalFilter, which are
 * part of Spring Cloud Gateway's own internal filter chain and therefore execute inside the
 * WebFlux WebFilter pipeline this sits in front of). Ensures every request - including ones
 * rejected at the edge by rate limiting or JWT validation - carries the same correlation id in
 * its logs as whatever downstream service ends up handling it, so one order's saga is greppable
 * across every service by this one id.
 *
 * <p>MDC in a reactive filter chain needs Reactor Context propagation to actually reach log
 * statements running on different threads later in the chain - {@code contextWrite} below plus
 * Spring Boot's built-in Reactor-Context-to-MDC bridge (auto-configured when
 * io.micrometer:context-propagation is on the classpath, which it is transitively here) is what
 * makes that work, not the MDC.put call alone.
 */
@Component
public class CorrelationIdWebFilter implements WebFilter, Ordered {

    private static final String HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        String finalCorrelationId = correlationId;

        exchange.getResponse().getHeaders().add(HEADER, finalCorrelationId);
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(r -> r.header(HEADER, finalCorrelationId))
                .build();

        return chain.filter(mutatedExchange)
                .contextWrite(Context.of(MDC_KEY, finalCorrelationId));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
