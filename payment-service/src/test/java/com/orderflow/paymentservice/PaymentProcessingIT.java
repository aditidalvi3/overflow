package com.orderflow.paymentservice;

import com.orderflow.paymentservice.dto.PaymentResponse;
import com.orderflow.paymentservice.entity.PaymentStatus;
import com.orderflow.paymentservice.kafka.event.InventoryReservedEvent;
import com.orderflow.paymentservice.kafka.event.OrderCreatedEvent;
import com.orderflow.paymentservice.kafka.event.PaymentFailedEvent;
import com.orderflow.paymentservice.kafka.event.PaymentProcessedEvent;
import com.orderflow.paymentservice.repository.PendingChargeRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises payment-service's saga participation: order.created upserts a PendingCharge, then
 * inventory.reserved triggers the (mock) gateway charge and publishes payment.processed /
 * payment.failed depending on the paymentToken, per MockPaymentGateway's token contract.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentProcessingIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orderflow")
            .withUsername("orderflow")
            .withPassword("orderflow");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PendingChargeRepository pendingChargeRepository;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private <T> void publish(String topic, Long orderId, T event) {
        Map<String, Object> props = KafkaTestUtils.producerProps(kafka.getBootstrapServers());
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

    /** Waits for OrderCreatedListener to have upserted the PendingCharge, avoiding a race with
     * publishing inventory.reserved before order.created has actually been consumed. */
    private void awaitPendingCharge(Long orderId) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (pendingChargeRepository.existsById(orderId)) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new AssertionError("PendingCharge for order " + orderId + " was never created");
    }

    private void placeSimulatedOrder(Long orderId, Long totalCents, String paymentToken) {
        publish("order.created", orderId, new OrderCreatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), orderId, 1L, totalCents, paymentToken,
                List.of(new OrderCreatedEvent.OrderItemPayload(1L, 2, totalCents / 2)), Instant.now()));
        awaitPendingCharge(orderId);
    }

    @Test
    void successfulCharge_publishesPaymentProcessed_andPersistsPayment() {
        Long orderId = System.nanoTime();
        placeSimulatedOrder(orderId, 2000L, null);

        publish("inventory.reserved", orderId,
                new InventoryReservedEvent(UUID.randomUUID(), UUID.randomUUID(), orderId, Instant.now()));

        PaymentProcessedEvent processed = waitForEvent("payment.processed", PaymentProcessedEvent.class,
                e -> e.orderId().equals(orderId));
        assertThat(processed.amountCents()).isEqualTo(2000L);
        assertThat(processed.providerRef()).startsWith("mock_");

        var response = restTemplate.getForEntity(baseUrl() + "/internal/payments/" + orderId, PaymentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(response.getBody().amountCents()).isEqualTo(2000L);
    }

    @Test
    void tokFail_publishesPaymentFailed_withDeclineReason() {
        Long orderId = System.nanoTime();
        placeSimulatedOrder(orderId, 1500L, "tok_fail");

        publish("inventory.reserved", orderId,
                new InventoryReservedEvent(UUID.randomUUID(), UUID.randomUUID(), orderId, Instant.now()));

        PaymentFailedEvent failed = waitForEvent("payment.failed", PaymentFailedEvent.class,
                e -> e.orderId().equals(orderId));
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.reason()).isEqualTo("Card declined by issuing bank");

        var response = restTemplate.getForEntity(baseUrl() + "/internal/payments/" + orderId, PaymentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getBody().failureReason()).isEqualTo("Card declined by issuing bank");
    }

    @Test
    void unknownOrder_paymentLookup_returns404() {
        var response = restTemplate.getForEntity(baseUrl() + "/internal/payments/" + System.nanoTime(), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
