package com.orderflow.apigateway;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Exercises the gateway as a black box: routing to stubbed downstream services, the JWT filter's
 * accept/reject behavior, and correlation-id propagation. Rate limiting has its own dedicated
 * test class ({@link RateLimitFilterIT}) since it needs a much lower bucket capacity than these
 * routing/auth scenarios can tolerate sharing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIT {

    // Matches application.yml's default orderflow.jwt.secret / order-service's JwtUtil - the
    // gateway only verifies tokens, order-service is the sole issuer.
    private static final String JWT_SECRET = "ZmFrZS1kZXYtc2VjcmV0LWtleS1jaGFuZ2UtbWUtaW4tcHJvZHVjdGlvbi0xMjM0NTY=";
    private static final SecretKey JWT_KEY = Keys.hmacShaKeyFor(Base64.getDecoder().decode(JWT_SECRET));

    private static HttpServer orderServiceStub;
    private static HttpServer inventoryServiceStub;

    @BeforeAll
    static void startStubs() throws IOException {
        orderServiceStub = startStub("{\"stub\":\"order-service\"}");
        inventoryServiceStub = startStub("{\"stub\":\"inventory-service\"}");
    }

    private static HttpServer startStub(String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return server;
    }

    @AfterAll
    static void stopStubs() {
        orderServiceStub.stop(0);
        inventoryServiceStub.stop(0);
    }

    // application.yml defines route[0]=order-service, route[1]=inventory-service; overriding just
    // the .uri of each indexed route element merges with the id/predicates/filters that still
    // come from application.yml, redirecting both routes at our in-process stubs.
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gateway.routes[0].uri",
                () -> "http://localhost:" + orderServiceStub.getAddress().getPort());
        registry.add("spring.cloud.gateway.routes[1].uri",
                () -> "http://localhost:" + inventoryServiceStub.getAddress().getPort());
    }

    @Autowired
    private WebTestClient webTestClient;

    private String token(long userId, String role, SecretKey signingKey) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", "user" + userId + "@example.com")
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofHours(1))))
                .signWith(signingKey)
                .compact();
    }

    @Test
    void requestWithoutToken_isRoutedThrough_toInventoryServiceStub() {
        webTestClient.get().uri("/api/products/1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Correlation-Id")
                .expectBody().jsonPath("$.stub").isEqualTo("inventory-service");
    }

    @Test
    void requestWithValidToken_isRoutedThrough_toOrderServiceStub() {
        String token = token(1L, "CUSTOMER", JWT_KEY);

        webTestClient.get().uri("/api/orders")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.stub").isEqualTo("order-service");
    }

    @Test
    void requestWithMalformedBearerToken_isRejectedWithUnauthorized() {
        webTestClient.get().uri("/api/orders")
                .header("Authorization", "Bearer this.is-not.a-valid-jwt")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.status").isEqualTo(401);
    }

    @Test
    void requestWithTokenSignedByDifferentKey_isRejectedWithUnauthorized() {
        SecretKey otherKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(
                "YW5vdGhlci1mYWtlLXNlY3JldC1rZXktZm9yLXRlc3RpbmctcHVycG9zZXMtb25seQ=="));
        String token = token(1L, "CUSTOMER", otherKey);

        webTestClient.get().uri("/api/orders")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void correlationId_isEchoedBack_whenSuppliedByClient() {
        webTestClient.get().uri("/api/products/1")
                .header("X-Correlation-Id", "test-corr-123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", "test-corr-123");
    }
}
