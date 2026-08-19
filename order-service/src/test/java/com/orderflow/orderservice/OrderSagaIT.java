package com.orderflow.orderservice;

import com.orderflow.orderservice.dto.AuthResponse;
import com.orderflow.orderservice.dto.OrderItemRequest;
import com.orderflow.orderservice.dto.OrderResponse;
import com.orderflow.orderservice.dto.PlaceOrderRequest;
import com.orderflow.orderservice.dto.RegisterRequest;
import com.orderflow.orderservice.entity.OrderStatus;
import com.orderflow.orderservice.kafka.Topics;
import com.orderflow.orderservice.kafka.event.InventoryReservationFailedEvent;
import com.orderflow.orderservice.kafka.event.OrderCancelledEvent;
import com.orderflow.orderservice.kafka.event.OrderConfirmedEvent;
import com.orderflow.orderservice.kafka.event.OrderCreatedEvent;
import com.orderflow.orderservice.kafka.event.OrderFailedEvent;
import com.orderflow.orderservice.kafka.event.PaymentFailedEvent;
import com.orderflow.orderservice.kafka.event.PaymentProcessedEvent;
import com.redis.testcontainers.RedisContainer;
import com.sun.net.httpserver.HttpServer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the order-service side of the choreography saga end-to-end: placing an order prices it
 * against a stubbed inventory-service, publishes {@code order.created}, and the order's status is
 * then driven forward by simulating the downstream events that payment-service/inventory-service
 * would publish back onto Kafka.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderSagaIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orderflow")
            .withUsername("orderflow")
            .withPassword("orderflow");

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    private static HttpServer inventoryStub;

    @BeforeAll
    static void startInventoryStub() throws IOException {
        inventoryStub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        inventoryStub.createContext("/api/products/", exchange -> {
            String body = "{\"id\":1,\"sku\":\"SKU-1\",\"name\":\"Widget\",\"priceCents\":1000,\"quantityAvailable\":100}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        inventoryStub.start();
    }

    @AfterAll
    static void stopInventoryStub() {
        inventoryStub.stop(0);
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("orderflow.inventory-service.base-url",
                () -> "http://localhost:" + inventoryStub.getAddress().getPort());
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private String registerAndLogin(String email) {
        RegisterRequest register = new RegisterRequest(email, "password123");
        var response = restTemplate.postForEntity(baseUrl() + "/api/auth/register", register, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().token();
    }

    private HttpHeaders authHeaders(String token, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        if (idempotencyKey != null) {
            headers.add("Idempotency-Key", idempotencyKey);
        }
        return headers;
    }

    private OrderResponse placeOrder(String token, String idempotencyKey, String paymentToken) {
        PlaceOrderRequest request = new PlaceOrderRequest(List.of(new OrderItemRequest(1L, 2)), paymentToken);
        var response = restTemplate.exchange(baseUrl() + "/api/orders", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(token, idempotencyKey)), OrderResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.PENDING);
        return response.getBody();
    }

    private OrderResponse pollOrderStatus(Long orderId, String token, OrderStatus expected) {
        long deadline = System.currentTimeMillis() + 15_000;
        OrderResponse last = null;
        while (System.currentTimeMillis() < deadline) {
            var response = restTemplate.exchange(baseUrl() + "/api/orders/" + orderId, HttpMethod.GET,
                    new HttpEntity<>(authHeaders(token, null)), OrderResponse.class);
            last = response.getBody();
            if (last != null && last.status() == expected) {
                return last;
            }
            sleep();
        }
        throw new AssertionError("Order " + orderId + " never reached " + expected + "; last seen: "
                + (last == null ? "null" : last.status()));
    }

    private static void sleep() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private <T> void publish(String topic, Long key, T event) {
        Map<String, Object> props = KafkaTestUtils.producerProps(kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        DefaultKafkaProducerFactory<String, Object> factory = new DefaultKafkaProducerFactory<>(props);
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(factory);
        try {
            template.send(topic, String.valueOf(key), event).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            factory.destroy();
        }
    }

    private <T> Consumer<String, T> consumerFor(Class<T> type) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                kafka.getBootstrapServers(), "it-" + UUID.randomUUID(), "true");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        DefaultKafkaConsumerFactory<String, T> factory = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new JsonDeserializer<>(type, false).trustedPackages("*"));
        return factory.createConsumer();
    }

    private <T> T waitForEvent(String topic, Class<T> type, Predicate<T> matches) {
        try (Consumer<String, T> consumer = consumerFor(type)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, T> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, T> record : records) {
                    if (matches.test(record.value())) {
                        return record.value();
                    }
                }
            }
        }
        throw new AssertionError("Timed out waiting for a matching event on topic " + topic);
    }

    @Test
    void placeOrder_publishesOrderCreatedEvent_withPricedItems() {
        String token = registerAndLogin("saga-created@example.com");
        OrderResponse order = placeOrder(token, "key-created-1", "tok_ok");

        OrderCreatedEvent event = waitForEvent(Topics.ORDER_CREATED, OrderCreatedEvent.class,
                e -> e.orderId().equals(order.id()));

        assertThat(event.totalCents()).isEqualTo(2000L);
        assertThat(event.items()).hasSize(1);
        assertThat(event.items().get(0).productId()).isEqualTo(1L);
        assertThat(event.items().get(0).quantity()).isEqualTo(2);
        assertThat(event.items().get(0).unitPriceCents()).isEqualTo(1000L);
    }

    @Test
    void paymentProcessedEvent_marksOrderPaid_andPublishesOrderConfirmed() {
        String token = registerAndLogin("saga-paid@example.com");
        OrderResponse order = placeOrder(token, "key-paid-1", "tok_ok");
        String correlationId = UUID.randomUUID().toString();

        publish(Topics.PAYMENT_PROCESSED, order.id(), new PaymentProcessedEvent(
                UUID.randomUUID().toString(), correlationId, order.id(), 999L, order.totalCents(),
                "prov_ref_1", Instant.now()));

        pollOrderStatus(order.id(), token, OrderStatus.PAID);

        OrderConfirmedEvent confirmed = waitForEvent(Topics.ORDER_CONFIRMED, OrderConfirmedEvent.class,
                e -> e.orderId().equals(order.id()));
        assertThat(confirmed.correlationId()).isEqualTo(correlationId);
    }

    @Test
    void paymentFailedEvent_marksOrderPaymentFailed_andPublishesOrderFailedWithReason() {
        String token = registerAndLogin("saga-payfail@example.com");
        OrderResponse order = placeOrder(token, "key-payfail-1", "tok_fail");

        publish(Topics.PAYMENT_FAILED, order.id(), new PaymentFailedEvent(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), order.id(),
                "FAILED", "Card declined by issuing bank", Instant.now()));

        pollOrderStatus(order.id(), token, OrderStatus.PAYMENT_FAILED);

        OrderFailedEvent failed = waitForEvent(Topics.ORDER_FAILED, OrderFailedEvent.class,
                e -> e.orderId().equals(order.id()));
        assertThat(failed.reason()).isEqualTo("Card declined by issuing bank");
    }

    @Test
    void inventoryReservationFailedEvent_marksOrderInventoryFailed() {
        String token = registerAndLogin("saga-invfail@example.com");
        OrderResponse order = placeOrder(token, "key-invfail-1", "tok_ok");

        publish(Topics.INVENTORY_RESERVATION_FAILED, order.id(), new InventoryReservationFailedEvent(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), order.id(),
                "Insufficient stock", Instant.now()));

        pollOrderStatus(order.id(), token, OrderStatus.INVENTORY_FAILED);
    }

    @Test
    void cancelOrder_paidOrder_publishesOrderCancelled() {
        String token = registerAndLogin("saga-cancel@example.com");
        OrderResponse order = placeOrder(token, "key-cancel-1", "tok_ok");

        publish(Topics.PAYMENT_PROCESSED, order.id(), new PaymentProcessedEvent(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), order.id(), 998L,
                order.totalCents(), "prov_ref_2", Instant.now()));
        pollOrderStatus(order.id(), token, OrderStatus.PAID);

        var cancelResponse = restTemplate.exchange(baseUrl() + "/api/orders/" + order.id() + "/cancel",
                HttpMethod.POST, new HttpEntity<>(authHeaders(token, null)), OrderResponse.class);
        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelResponse.getBody().status()).isEqualTo(OrderStatus.CANCELLED);

        waitForEvent(Topics.ORDER_CANCELLED, OrderCancelledEvent.class, e -> e.orderId().equals(order.id()));
    }
}
