package com.orderflow.orderservice.entity;

public enum OrderStatus {
    PENDING,
    INVENTORY_FAILED,
    PAYMENT_FAILED,
    PAID,
    CANCELLED
}
