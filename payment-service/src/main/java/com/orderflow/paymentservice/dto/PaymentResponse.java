package com.orderflow.paymentservice.dto;

import com.orderflow.paymentservice.entity.Payment;
import com.orderflow.paymentservice.entity.PaymentStatus;

import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long orderId,
        Long amountCents,
        PaymentStatus status,
        String providerRef,
        String failureReason,
        Instant createdAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getAmountCents(),
                payment.getStatus(), payment.getProviderRef(), payment.getFailureReason(), payment.getCreatedAt());
    }
}
