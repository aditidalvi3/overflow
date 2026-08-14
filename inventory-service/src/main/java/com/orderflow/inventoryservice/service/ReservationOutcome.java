package com.orderflow.inventoryservice.service;

public record ReservationOutcome(boolean success, String failureReason) {

    public static ReservationOutcome succeeded() {
        return new ReservationOutcome(true, null);
    }

    public static ReservationOutcome failure(String reason) {
        return new ReservationOutcome(false, reason);
    }
}
