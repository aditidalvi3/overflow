package com.orderflow.orderservice.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "total_cents", nullable = false)
    private Long totalCents;

    // The saga correlationId minted when this order is first created. Persisted (rather than
    // only carried on the triggering Kafka event) because order-service also needs to emit
    // events for this order outside of any inbound event handler - e.g. order.cancelled, which
    // is triggered by a REST call, not a saga event - and those must still carry the same
    // correlationId as order.created so the whole saga for this order stays correlated.
    @Column(name = "correlation_id", nullable = false, length = 36)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Order() {
    }

    public Order(Long userId, Long totalCents, OrderStatus status, String correlationId) {
        this.userId = userId;
        this.totalCents = totalCents;
        this.status = status;
        this.correlationId = correlationId;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Long getTotalCents() {
        return totalCents;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markInventoryFailed() {
        status = OrderStatus.INVENTORY_FAILED;
    }

    public void markPaid() {
        status = OrderStatus.PAID;
    }

    public void markPaymentFailed() {
        status = OrderStatus.PAYMENT_FAILED;
    }

    public void cancel() {
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException("Only PAID orders can be cancelled");
        }
        status = OrderStatus.CANCELLED;
    }
}
