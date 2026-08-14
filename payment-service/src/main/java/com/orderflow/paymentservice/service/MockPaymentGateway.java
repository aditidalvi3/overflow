package com.orderflow.paymentservice.service;

import com.orderflow.paymentservice.entity.PaymentStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stands in for a real gateway. Outcome is chosen by paymentToken so callers can force a scenario
 * deterministically, the way Stripe's test-mode tokens work: blank/unrecognized -> SUCCEEDED,
 * "tok_fail" -> FAILED, "tok_timeout" -> TIMEOUT, "tok_random" -> weighted random across all
 * three. Active by default; set orderflow.payment.provider=stripe to use
 * {@link StripePaymentGateway} instead (real Stripe test-mode calls) without touching
 * {@link PaymentProcessingService}.
 */
@Service
@ConditionalOnProperty(prefix = "orderflow.payment", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    private static final long SIMULATED_TIMEOUT_MILLIS = 250;

    public PaymentOutcome charge(long amountCents, String paymentToken) {
        String token = paymentToken == null ? "" : paymentToken.trim().toLowerCase();

        return switch (token) {
            case "tok_fail" -> failed("Card declined by issuing bank");
            case "tok_timeout" -> {
                simulateNetworkDelay();
                yield timedOut();
            }
            case "tok_random" -> randomOutcome();
            default -> succeeded();
        };
    }

    private PaymentOutcome randomOutcome() {
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 90) {
            return succeeded();
        }
        if (roll < 97) {
            return failed("Card declined by issuing bank");
        }
        simulateNetworkDelay();
        return timedOut();
    }

    private void simulateNetworkDelay() {
        try {
            Thread.sleep(SIMULATED_TIMEOUT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private PaymentOutcome succeeded() {
        return new PaymentOutcome(PaymentStatus.SUCCEEDED, providerRef(), null);
    }

    private PaymentOutcome failed(String reason) {
        return new PaymentOutcome(PaymentStatus.FAILED, providerRef(), reason);
    }

    private PaymentOutcome timedOut() {
        return new PaymentOutcome(PaymentStatus.TIMEOUT, providerRef(),
                "Gateway did not respond within " + SIMULATED_TIMEOUT_MILLIS + "ms");
    }

    private String providerRef() {
        return "mock_" + UUID.randomUUID();
    }
}
