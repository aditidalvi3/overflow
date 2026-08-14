package com.orderflow.inventoryservice.kafka;

import com.orderflow.inventoryservice.kafka.event.InventoryReleasedEvent;
import com.orderflow.inventoryservice.kafka.event.InventoryReservationFailedEvent;
import com.orderflow.inventoryservice.kafka.event.InventoryReservedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class InventoryEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InventoryEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishReserved(UUID correlationId, Long orderId) {
        send(KafkaTopics.INVENTORY_RESERVED, orderId,
                new InventoryReservedEvent(UUID.randomUUID(), correlationId, orderId, Instant.now()));
    }

    public void publishReservationFailed(UUID correlationId, Long orderId, String reason) {
        send(KafkaTopics.INVENTORY_RESERVATION_FAILED, orderId,
                new InventoryReservationFailedEvent(UUID.randomUUID(), correlationId, orderId, reason, Instant.now()));
    }

    public void publishReleased(UUID correlationId, Long orderId) {
        send(KafkaTopics.INVENTORY_RELEASED, orderId,
                new InventoryReleasedEvent(UUID.randomUUID(), correlationId, orderId, Instant.now()));
    }

    private void send(String topic, Long orderId, Object payload) {
        kafkaTemplate.send(topic, String.valueOf(orderId), payload);
    }
}
