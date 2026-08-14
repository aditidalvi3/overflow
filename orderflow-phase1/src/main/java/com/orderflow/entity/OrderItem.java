package com.orderflow.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price_cents", nullable = false)
    private Long unitPriceCents;

    protected OrderItem() {
    }

    public OrderItem(Long orderId, Long productId, Integer quantity, Long unitPriceCents) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPriceCents = unitPriceCents;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Long getUnitPriceCents() {
        return unitPriceCents;
    }
}
