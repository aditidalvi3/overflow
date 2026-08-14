package com.orderflow.service;

import com.orderflow.entity.PaymentStatus;

public record PaymentOutcome(PaymentStatus status, String providerRef, String failureReason) {
}
