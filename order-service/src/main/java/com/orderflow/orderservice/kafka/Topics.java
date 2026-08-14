package com.orderflow.orderservice.kafka;

public final class Topics {

    public static final String ORDER_CREATED = "order.created";
    public static final String INVENTORY_RESERVATION_FAILED = "inventory.reservation-failed";
    public static final String PAYMENT_PROCESSED = "payment.processed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String ORDER_CONFIRMED = "order.confirmed";
    public static final String ORDER_FAILED = "order.failed";

    private Topics() {
    }
}
