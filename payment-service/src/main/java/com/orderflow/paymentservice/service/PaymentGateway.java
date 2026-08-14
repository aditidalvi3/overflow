package com.orderflow.paymentservice.service;

/**
 * Seam between {@link PaymentProcessingService} and whichever charge provider is active.
 * {@link MockPaymentGateway} (default) and {@link StripePaymentGateway} (real Stripe test-mode
 * calls, active when {@code orderflow.payment.provider=stripe}) both implement this — swapping
 * providers is a config change, not a code change.
 */
public interface PaymentGateway {

    PaymentOutcome charge(long amountCents, String paymentToken);
}
