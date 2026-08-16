package com.orderflow.apigateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolated in its own context (separate from {@link GatewayRoutingIT}) with a deliberately tiny
 * token-bucket capacity and near-zero refill, mirroring TokenBucketRateLimiterTest's unit-test
 * pattern - a shared context with the routing/auth tests would make this flaky depending on test
 * execution order, since RateLimitGlobalFilter buckets per client IP and every request (including
 * unauthenticated ones) costs a token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "orderflow.rate-limit.capacity=3",
        "orderflow.rate-limit.refill-per-second=0.0001"
})
class RateLimitFilterIT {

    private static HttpServer orderServiceStub;

    @BeforeAll
    static void startStub() throws IOException {
        orderServiceStub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        orderServiceStub.createContext("/", exchange -> {
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        orderServiceStub.start();
    }

    @AfterAll
    static void stopStub() {
        orderServiceStub.stop(0);
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gateway.routes[0].uri",
                () -> "http://localhost:" + orderServiceStub.getAddress().getPort());
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void exceedingCapacity_returns429_withRetryAfterHeader_andExpectedBody() {
        for (int attempt = 0; attempt < 20; attempt++) {
            var result = webTestClient.get().uri("/api/orders").exchange().returnResult(String.class);
            if (result.getStatus().value() == 429) {
                assertThat(result.getResponseHeaders().getFirst("Retry-After")).isEqualTo("1");
                assertThat(result.getResponseBody().blockFirst()).contains("\"status\":429");
                return;
            }
        }
        throw new AssertionError("Never observed a 429 within 20 requests against a capacity-3 bucket");
    }
}
