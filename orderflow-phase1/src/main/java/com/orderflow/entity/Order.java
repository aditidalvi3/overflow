package com.orderflow.entity;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Order() {
    }

    public Order(Long userId, Long totalCents, OrderStatus status) {
        this.userId = userId;
        this.totalCents = totalCents;
        this.status = status;
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

    public void cancel() {
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException("Only PAID orders can be cancelled");
        }
        status = OrderStatus.CANCELLED;
    }

    public Long getTotalCents() {
        return totalCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
