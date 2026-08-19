package com.orderflow.inventoryservice;

import com.orderflow.inventoryservice.entity.Inventory;
import com.orderflow.inventoryservice.entity.Product;
import com.orderflow.inventoryservice.kafka.KafkaTopics;
import com.orderflow.inventoryservice.kafka.event.InventoryReleasedEvent;
import com.orderflow.inventoryservice.kafka.event.InventoryReservationFailedEvent;
import com.orderflow.inventoryservice.kafka.event.InventoryReservedEvent;
import com.orderflow.inventoryservice.kafka.event.OrderCancelledEvent;
import com.orderflow.inventoryservice.kafka.event.OrderCreatedEvent;
import com.orderflow.inventoryservice.kafka.event.PaymentFailedEvent;
import com.orderflow.inventoryservice.repository.InventoryRepository;
import com.orderflow.inventoryservice.repository.ProductRepository;
import com.redis.testcontainers.RedisContainer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises inventory-service's three Kafka listeners against a real broker: reserving stock on
 * {@code order.created}, and releasing it again on {@code payment.failed} / {@code order.cancelled}
 * (both compensating paths share InventoryReleaseService).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventorySagaIT {

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

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    private Long seedProduct(String sku, int initialQuantity) {
        Product product = productRepository.save(new Product(sku, "Test product", 1000L));
        inventoryRepository.save(new Inventory(product.getId(), initialQuantity));
        return product.getId();
    }

    private <T> void publish(String topic, Long orderId, T event) {
        Map<String, Object> props = KafkaTestUtils.producerProps(kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        DefaultKafkaProducerFactory<String, Object> factory = new DefaultKafkaProducerFactory<>(props);
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(factory);
        try {
            template.send(topic, String.valueOf(orderId), event).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            factory.destroy();
        }
    }

    private <T> T waitForEvent(String topic, Class<T> type, Predicate<T> matches) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                kafka.getBootstrapServers(), "it-" + UUID.randomUUID(), "true");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        DefaultKafkaConsumerFactory<String, T> factory = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new JsonDeserializer<>(type, false).trustedPackages("*"));
        try (Consumer<String, T> consumer = factory.createConsumer()) {
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

    private Inventory awaitInventory(Long productId, Predicate<Inventory> matches) {
        long deadline = System.currentTimeMillis() + 15_000;
        Inventory last = null;
        while (System.currentTimeMillis() < deadline) {
            last = inventoryRepository.findById(productId).orElse(null);
            if (last != null && matches.test(last)) {
                return last;
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new AssertionError("Inventory for product " + productId + " never matched expected condition; last seen: "
                + (last == null ? "null" : last.getQuantityAvailable()));
    }

    @Test
    void orderCreated_sufficientStock_reservesStock_andPublishesReserved() {
        Long productId = seedProduct("SAGA-SKU-1", 10);
        Long orderId = System.nanoTime();
        UUID correlationId = UUID.randomUUID();

        publish(KafkaTopics.ORDER_CREATED, orderId, new OrderCreatedEvent(
                UUID.randomUUID(), correlationId, orderId, 1L, 3000L, null,
                List.of(new OrderCreatedEvent.Item(productId, 3, 1000L)), Instant.now()));

        InventoryReservedEvent reserved = waitForEvent(KafkaTopics.INVENTORY_RESERVED, InventoryReservedEvent.class,
                e -> e.orderId().equals(orderId));
        assertThat(reserved.correlationId()).isEqualTo(correlationId);

        awaitInventory(productId, inv -> inv.getQuantityAvailable() == 7);
    }

    @Test
    void orderCreated_insufficientStock_publishesReservationFailed_andLeavesStockUntouched() {
        Long productId = seedProduct("SAGA-SKU-2", 1);
        Long orderId = System.nanoTime();

        publish(KafkaTopics.ORDER_CREATED, orderId, new OrderCreatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), orderId, 1L, 5000L, null,
                List.of(new OrderCreatedEvent.Item(productId, 5, 1000L)), Instant.now()));

        InventoryReservationFailedEvent failed = waitForEvent(
                KafkaTopics.INVENTORY_RESERVATION_FAILED, InventoryReservationFailedEvent.class,
                e -> e.orderId().equals(orderId));
        assertThat(failed.reason()).contains("SAGA-SKU-2");

        Inventory unchanged = inventoryRepository.findById(productId).orElseThrow();
        assertThat(unchanged.getQuantityAvailable()).isEqualTo(1);
    }

    @Test
    void paymentFailed_releasesPreviouslyReservedStock() {
        Long productId = seedProduct("SAGA-SKU-3", 10);
        Long orderId = System.nanoTime();

        publish(KafkaTopics.ORDER_CREATED, orderId, new OrderCreatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), orderId, 1L, 4000L, null,
                List.of(new OrderCreatedEvent.Item(productId, 4, 1000L)), Instant.now()));
        waitForEvent(KafkaTopics.INVENTORY_RESERVED, InventoryReservedEvent.class, e -> e.orderId().equals(orderId));
        awaitInventory(productId, inv -> inv.getQuantityAvailable() == 6);

        publish(KafkaTopics.PAYMENT_FAILED, orderId, new PaymentFailedEvent(
                UUID.randomUUID(), UUID.randomUUID(), orderId, "FAILED", "Card declined", Instant.now()));

        waitForEvent(KafkaTopics.INVENTORY_RELEASED, InventoryReleasedEvent.class, e -> e.orderId().equals(orderId));
        awaitInventory(productId, inv -> inv.getQuantityAvailable() == 10);
    }

    @Test
    void orderCancelled_releasesPreviouslyReservedStock() {
        Long productId = seedProduct("SAGA-SKU-4", 5);
        Long orderId = System.nanoTime();

        publish(KafkaTopics.ORDER_CREATED, orderId, new OrderCreatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), orderId, 1L, 2000L, null,
                List.of(new OrderCreatedEvent.Item(productId, 2, 1000L)), Instant.now()));
        waitForEvent(KafkaTopics.INVENTORY_RESERVED, InventoryReservedEvent.class, e -> e.orderId().equals(orderId));
        awaitInventory(productId, inv -> inv.getQuantityAvailable() == 3);

        publish(KafkaTopics.ORDER_CANCELLED, orderId,
                new OrderCancelledEvent(UUID.randomUUID(), UUID.randomUUID(), orderId, Instant.now()));

        waitForEvent(KafkaTopics.INVENTORY_RELEASED, InventoryReleasedEvent.class, e -> e.orderId().equals(orderId));
        awaitInventory(productId, inv -> inv.getQuantityAvailable() == 5);
    }
}
