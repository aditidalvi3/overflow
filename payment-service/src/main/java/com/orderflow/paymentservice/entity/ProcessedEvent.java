package com.orderflow.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "processed_events", uniqueConstraints = @UniqueConstraint(columnNames = {"topic", "order_id"}))
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String topic;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "processed_at", nullable = false)
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
