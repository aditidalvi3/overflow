package com.orderflow.inventoryservice.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(name = "price_cents", nullable = false)
    private Long priceCents;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Product() {
    }

    public Product(String sku, String name, Long priceCents) {
        this.sku = sku;
        this.name = name;
        this.priceCents = priceCents;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public Long getPriceCents() {
        return priceCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
