package com.orderflow.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Local cache of what order.created told us (amount + payment token), keyed by orderId. The
 * later inventory.reserved event only carries {orderId}, so this is how payment-service learns
 * what to actually charge when that event arrives.
 */
@Entity
@Table(name = "pending_charges")
public class PendingCharge {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "payment_token")
    private String paymentToken;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    protected PendingCharge() {
    }

    public PendingCharge(Long orderId, Long amountCents, String paymentToken) {
        this.orderId = orderId;
        this.amountCents = amountCents;
        this.paymentToken = paymentToken;
        this.receivedAt = Instant.now();
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(Long amountCents) {
        this.amountCents = amountCents;
    }

    public String getPaymentToken() {
        return paymentToken;
    }

    public void setPaymentToken(String paymentToken) {
        this.paymentToken = paymentToken;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
}
