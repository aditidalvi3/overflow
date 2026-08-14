package com.orderflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.dto.*;
import com.orderflow.entity.OrderStatus;
import com.orderflow.entity.PaymentStatus;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderPlacementIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orderflow")
            .withUsername("orderflow")
            .withPassword("orderflow");

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private String registerAndLogin(String email) {
        RegisterRequest register = new RegisterRequest(email, "password123");
        var response = restTemplate.postForEntity(baseUrl() + "/api/auth/register", register, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().token();
    }

    private String loginAsAdmin() {
        // Seeded on startup by AdminBootstrapRunner from orderflow.admin.email/password (application.yml defaults).
        LoginRequest login = new LoginRequest("admin@orderflow.local", "ChangeMe123!");
        var response = restTemplate.postForEntity(baseUrl() + "/api/auth/login", login, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().token();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private Long createProductAsAdmin(String sku, String name, long priceCents, int initialQuantity) {
        CreateProductRequest createProduct = new CreateProductRequest(sku, name, priceCents, initialQuantity);
        var response = restTemplate.postForEntity(
                baseUrl() + "/api/products", new HttpEntity<>(createProduct, authHeaders(loginAsAdmin())),
                ProductResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().id();
    }

    @Test
    void placeOrder_withRepeatedIdempotencyKey_returnsSameOrder_andDoesNotDoubleDecrementStock() {
        Long productId = createProductAsAdmin("SKU-IT-1", "Widget", 500L, 10);
        String token = registerAndLogin("buyer@example.com");

        HttpHeaders orderHeaders = authHeaders(token);
        orderHeaders.add("Idempotency-Key", "order-key-1");
        PlaceOrderRequest placeOrderRequest = new PlaceOrderRequest(List.of(new OrderItemRequest(productId, 3)), null);

        var firstResponse = restTemplate.exchange(baseUrl() + "/api/orders", HttpMethod.POST,
                new HttpEntity<>(placeOrderRequest, orderHeaders), OrderResponse.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(firstResponse.getBody().status()).isEqualTo(OrderStatus.PAID);
        Long orderId = firstResponse.getBody().id();

        var secondResponse = restTemplate.exchange(baseUrl() + "/api/orders", HttpMethod.POST,
                new HttpEntity<>(placeOrderRequest, orderHeaders), OrderResponse.class);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(secondResponse.getBody().id()).isEqualTo(orderId);

        var productAfter = restTemplate.getForEntity(baseUrl() + "/api/products/" + productId, ProductResponse.class);
        assertThat(productAfter.getBody().quantityAvailable()).isEqualTo(7);
    }

    @Test
    void placeOrder_insufficientStock_returns409() {
        Long productId = createProductAsAdmin("SKU-IT-2", "Gadget", 500L, 1);
        String token = registerAndLogin("buyer2@example.com");

        HttpHeaders orderHeaders = authHeaders(token);
        orderHeaders.add("Idempotency-Key", "order-key-2");
        PlaceOrderRequest placeOrderRequest = new PlaceOrderRequest(List.of(new OrderItemRequest(productId, 5)), null);

        var response = restTemplate.exchange(baseUrl() + "/api/orders", HttpMethod.POST,
                new HttpEntity<>(placeOrderRequest, orderHeaders), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void placeOrder_paymentDeclined_releasesStock_andMarksOrderPaymentFailed() {
        Long productId = createProductAsAdmin("SKU-IT-3", "Gizmo", 500L, 10);
        String token = registerAndLogin("buyer3@example.com");

        HttpHeaders orderHeaders = authHeaders(token);
        orderHeaders.add("Idempotency-Key", "order-key-3");
        PlaceOrderRequest placeOrderRequest = new PlaceOrderRequest(List.of(new OrderItemRequest(productId, 4)), "tok_fail");

        var response = restTemplate.exchange(baseUrl() + "/api/orders", HttpMethod.POST,
                new HttpEntity<>(placeOrderRequest, orderHeaders), OrderResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(response.getBody().paymentStatus()).isEqualTo(PaymentStatus.FAILED);

        // Stock reserved during the failed attempt must be released back to the catalog.
        var productAfter = restTemplate.getForEntity(baseUrl() + "/api/products/" + productId, ProductResponse.class);
        assertThat(productAfter.getBody().quantityAvailable()).isEqualTo(10);
    }

    @Test
    void createProduct_asCustomer_returns403() {
        String token = registerAndLogin("nonadmin@example.com");
        CreateProductRequest createProduct = new CreateProductRequest("SKU-IT-4", "Forbidden", 500L, 5);

        var response = restTemplate.postForEntity(
                baseUrl() + "/api/products", new HttpEntity<>(createProduct, authHeaders(token)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
