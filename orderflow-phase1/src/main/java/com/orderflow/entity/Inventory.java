package com.orderflow.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "quantity_available", nullable = false)
    private Integer quantityAvailable;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Inventory() {
    }

    public Inventory(Long productId, Integer quantityAvailable) {
        this.productId = productId;
        this.quantityAvailable = quantityAvailable;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getQuantityAvailable() {
        return quantityAvailable;
    }

    public void decrease(int quantity) {
        if (quantityAvailable < quantity) {
            throw new IllegalStateException("Insufficient stock for product " + productId);
        }
        quantityAvailable -= quantity;
    }

    public void increase(int quantity) {
        quantityAvailable += quantity;
    }

    public Long getVersion() {
        return version;
    }
}
