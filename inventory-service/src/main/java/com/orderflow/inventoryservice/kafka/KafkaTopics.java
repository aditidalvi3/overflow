package com.orderflow.inventoryservice.kafka;

/** Topic name constants shared with every other OrderFlow service - names must match the contract exactly. */
public final class KafkaTopics {

    public static final String ORDER_CREATED = "order.created";
    public static final String INVENTORY_RESERVED = "inventory.reserved";
    public static final String INVENTORY_RESERVATION_FAILED = "inventory.reservation-failed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String INVENTORY_RELEASED = "inventory.released";
    public static final String ORDER_CANCELLED = "order.cancelled";

    private KafkaTopics() {
    }
}
