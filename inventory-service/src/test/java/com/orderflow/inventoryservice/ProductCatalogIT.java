package com.orderflow.inventoryservice;

import com.orderflow.inventoryservice.dto.CreateProductRequest;
import com.orderflow.inventoryservice.dto.PagedResponse;
import com.orderflow.inventoryservice.dto.ProductResponse;
import com.redis.testcontainers.RedisContainer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductCatalogIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orderflow")
            .withUsername("orderflow")
            .withPassword("orderflow");

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    // Matches application.yml's default orderflow.jwt.secret - inventory-service only verifies
    // tokens (order-service is the sole issuer), so tests sign their own with the shared secret.
    private static final String JWT_SECRET = "ZmFrZS1kZXYtc2VjcmV0LWtleS1jaGFuZ2UtbWUtaW4tcHJvZHVjdGlvbi0xMjM0NTY=";
    private static final SecretKey JWT_KEY = Keys.hmacShaKeyFor(Base64.getDecoder().decode(JWT_SECRET));

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private String token(long userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", "user" + userId + "@example.com")
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofHours(1))))
                .signWith(JWT_KEY)
                .compact();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private ProductResponse createProduct(String adminToken, String sku, String name, long priceCents, int quantity) {
        CreateProductRequest request = new CreateProductRequest(sku, name, priceCents, quantity);
        var response = restTemplate.postForEntity(
                baseUrl() + "/api/products", new HttpEntity<>(request, authHeaders(adminToken)), ProductResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    @Test
    void createProduct_asAdmin_succeeds_andIsRetrievableWithoutAuth() {
        String adminToken = token(1L, "ADMIN");
        ProductResponse created = createProduct(adminToken, "CAT-ADMIN-1", "Widget", 1000L, 10);

        var fetched = restTemplate.getForEntity(baseUrl() + "/api/products/" + created.id(), ProductResponse.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().sku()).isEqualTo("CAT-ADMIN-1");
        assertThat(fetched.getBody().quantityAvailable()).isEqualTo(10);
    }

    @Test
    void createProduct_asCustomer_returns403() {
        String customerToken = token(2L, "CUSTOMER");
        CreateProductRequest request = new CreateProductRequest("CAT-FORBIDDEN", "Nope", 500L, 5);

        var response = restTemplate.postForEntity(
                baseUrl() + "/api/products", new HttpEntity<>(request, authHeaders(customerToken)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createProduct_withoutAuth_returns401() {
        // No Authorization header, so Spring Security rejects this before the body is ever read -
        // sending no body at all sidesteps a JDK HttpURLConnection limitation where a POST body
        // already being streamed can't be replayed if the response comes back 401/403
        // ("HttpRetryException: cannot retry due to server authentication, in streaming mode").
        var response = restTemplate.postForEntity(baseUrl() + "/api/products", null, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createProduct_duplicateSku_returns409() {
        String adminToken = token(1L, "ADMIN");
        createProduct(adminToken, "CAT-DUPE", "Widget", 1000L, 10);

        CreateProductRequest duplicate = new CreateProductRequest("CAT-DUPE", "Widget Again", 1200L, 5);
        var response = restTemplate.postForEntity(
                baseUrl() + "/api/products", new HttpEntity<>(duplicate, authHeaders(adminToken)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void listProducts_filtersBySku_andPaginates_withoutAuth() {
        String adminToken = token(1L, "ADMIN");
        createProduct(adminToken, "CAT-WIDGET-1", "Widget One", 1000L, 5);
        createProduct(adminToken, "CAT-WIDGET-2", "Widget Two", 1200L, 5);
        createProduct(adminToken, "CAT-GADGET-1", "Gadget One", 1500L, 5);

        var page0 = restTemplate.exchange(
                baseUrl() + "/api/products?sku=CAT-WIDGET&page=0&size=1", HttpMethod.GET,
                null, new ParameterizedTypeReference<PagedResponse<ProductResponse>>() {});

        assertThat(page0.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page0.getBody().items()).hasSize(1);
        assertThat(page0.getBody().totalElements()).isEqualTo(2);
        assertThat(page0.getBody().totalPages()).isEqualTo(2);
        assertThat(page0.getBody().items().get(0).sku()).startsWith("CAT-WIDGET");

        var unfiltered = restTemplate.exchange(
                baseUrl() + "/api/products?name=Gadget", HttpMethod.GET,
                null, new ParameterizedTypeReference<PagedResponse<ProductResponse>>() {});
        assertThat(unfiltered.getBody().items()).hasSize(1);
        assertThat(unfiltered.getBody().items().get(0).sku()).isEqualTo("CAT-GADGET-1");
    }
}
