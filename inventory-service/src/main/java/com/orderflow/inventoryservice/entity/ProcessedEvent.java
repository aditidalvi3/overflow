package com.orderflow.inventoryservice.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String topic;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt = Instant.now();

    protected ProcessedEvent() {
    }

    public ProcessedEvent(String topic, Long orderId) {
        this.topic = topic;
        this.orderId = orderId;
    }

    public Long getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
