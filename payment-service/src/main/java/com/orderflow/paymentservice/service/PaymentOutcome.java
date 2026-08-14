package com.orderflow.paymentservice.service;

import com.orderflow.paymentservice.entity.PaymentStatus;

public record PaymentOutcome(PaymentStatus status, String providerRef, String failureReason) {
}
