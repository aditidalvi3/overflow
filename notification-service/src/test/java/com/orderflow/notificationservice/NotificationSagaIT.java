package com.orderflow.notificationservice;

import com.orderflow.notificationservice.dto.NotificationLogResponse;
import com.orderflow.notificationservice.entity.NotificationType;
import com.orderflow.notificationservice.kafka.OrderConfirmedEvent;
import com.orderflow.notificationservice.kafka.OrderFailedEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises notification-service's two Kafka listeners end-to-end: order.confirmed/order.failed
 * each produce a NotificationLog row, retrievable via GET /internal/notifications/{orderId}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationSagaIT {

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

    private String baseUrl() {
        return "http://localhost:" + port;
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

    private List<NotificationLogResponse> awaitLogsFor(Long orderId, int expectedCount) {
        long deadline = System.currentTimeMillis() + 15_000;
        List<NotificationLogResponse> last = List.of();
        while (System.currentTimeMillis() < deadline) {
            var response = restTemplate.exchange(baseUrl() + "/internal/notifications/" + orderId, HttpMethod.GET,
                    null, new ParameterizedTypeReference<List<NotificationLogResponse>>() {});
            last = response.getBody();
            if (last != null && last.size() >= expectedCount) {
                return last;
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new AssertionError("Notifications for order " + orderId + " never reached size " + expectedCount
                + "; last seen: " + last);
    }

    @Test
    void orderConfirmed_persistsNotificationLog() {
        Long orderId = System.nanoTime();

        publish("order.confirmed", orderId,
                new OrderConfirmedEvent(UUID.randomUUID(), UUID.randomUUID(), orderId, 1L, Instant.now()));

        List<NotificationLogResponse> logs = awaitLogsFor(orderId, 1);
        assertThat(logs.get(0).type()).isEqualTo(NotificationType.ORDER_CONFIRMED);
        assertThat(logs.get(0).channel()).isEqualTo("EMAIL");
        assertThat(logs.get(0).content()).contains(String.valueOf(orderId));
    }

    @Test
    void orderFailed_persistsNotificationLog_withReasonInContent() {
        Long orderId = System.nanoTime();

        publish("order.failed", orderId, new OrderFailedEvent(
                UUID.randomUUID(), UUID.randomUUID(), orderId, 1L, "Card declined", Instant.now()));

        List<NotificationLogResponse> logs = awaitLogsFor(orderId, 1);
        assertThat(logs.get(0).type()).isEqualTo(NotificationType.ORDER_FAILED);
        assertThat(logs.get(0).content()).contains("Card declined").contains(String.valueOf(orderId));
    }

    @Test
    void unknownOrder_returnsEmptyList() {
        var response = restTemplate.exchange(baseUrl() + "/internal/notifications/" + System.nanoTime(),
                HttpMethod.GET, null, new ParameterizedTypeReference<List<NotificationLogResponse>>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }
}
