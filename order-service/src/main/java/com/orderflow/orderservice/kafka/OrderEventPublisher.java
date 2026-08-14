package com.orderflow.orderservice.kafka;

import com.orderflow.orderservice.entity.Order;
import com.orderflow.orderservice.kafka.event.OrderCancelledEvent;
import com.orderflow.orderservice.kafka.event.OrderConfirmedEvent;
import com.orderflow.orderservice.kafka.event.OrderCreatedEvent;
import com.orderflow.orderservice.kafka.event.OrderCreatedItem;
import com.orderflow.orderservice.kafka.event.OrderFailedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(Order order, String correlationId, String paymentToken, List<OrderCreatedItem> items) {
        var event = new OrderCreatedEvent(newEventId(), correlationId, order.getId(), order.getUserId(),
                order.getTotalCents(), paymentToken, items, Instant.now());
        send(Topics.ORDER_CREATED, order.getId(), event);
    }

    public void publishOrderCancelled(Order order, String correlationId) {
        var event = new OrderCancelledEvent(newEventId(), correlationId, order.getId(), Instant.now());
        send(Topics.ORDER_CANCELLED, order.getId(), event);
    }

    public void publishOrderConfirmed(Order order, String correlationId) {
        var event = new OrderConfirmedEvent(newEventId(), correlationId, order.getId(), order.getUserId(), Instant.now());
        send(Topics.ORDER_CONFIRMED, order.getId(), event);
    }

    public void publishOrderFailed(Order order, String correlationId, String reason) {
        var event = new OrderFailedEvent(newEventId(), correlationId, order.getId(), order.getUserId(), reason, Instant.now());
        send(Topics.ORDER_FAILED, order.getId(), event);
    }

    private void send(String topic, Long orderId, Object event) {
        kafkaTemplate.send(topic, String.valueOf(orderId), event);
    }

    private static String newEventId() {
        return UUID.randomUUID().toString();
    }
}
