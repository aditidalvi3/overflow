package com.orderflow;

import com.orderflow.dto.*;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private String loginAsAdmin() {
        LoginRequest login = new LoginRequest("admin@orderflow.local", "ChangeMe123!");
        var response = restTemplate.postForEntity(baseUrl() + "/api/auth/login", login, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().token();
    }

    private void createProduct(String token, String sku, String name, long priceCents, int quantity) {
        CreateProductRequest request = new CreateProductRequest(sku, name, priceCents, quantity);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        var response = restTemplate.postForEntity(
                baseUrl() + "/api/products", new HttpEntity<>(request, headers), ProductResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void listProducts_filtersBySku_andPaginates_withoutAuth() {
        String adminToken = loginAsAdmin();
        createProduct(adminToken, "CAT-WIDGET-1", "Widget One", 1000L, 5);
        createProduct(adminToken, "CAT-WIDGET-2", "Widget Two", 1200L, 5);
        createProduct(adminToken, "CAT-GADGET-1", "Gadget One", 1500L, 5);

        var page0 = restTemplate.exchange(
                baseUrl() + "/api/products?sku=CAT-WIDGET&page=0&size=1", org.springframework.http.HttpMethod.GET,
                null, new ParameterizedTypeReference<PagedResponse<ProductResponse>>() {});

        assertThat(page0.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page0.getBody().items()).hasSize(1);
        assertThat(page0.getBody().totalElements()).isEqualTo(2);
        assertThat(page0.getBody().totalPages()).isEqualTo(2);
        assertThat(page0.getBody().items().get(0).sku()).startsWith("CAT-WIDGET");

        var unfiltered = restTemplate.exchange(
                baseUrl() + "/api/products?name=Gadget", org.springframework.http.HttpMethod.GET,
                null, new ParameterizedTypeReference<PagedResponse<ProductResponse>>() {});
        assertThat(unfiltered.getBody().items()).hasSize(1);
        assertThat(unfiltered.getBody().items().get(0).sku()).isEqualTo("CAT-GADGET-1");
    }
}
