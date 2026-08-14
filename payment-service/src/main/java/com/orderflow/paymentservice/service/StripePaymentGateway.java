package com.orderflow.paymentservice.service;

import com.orderflow.paymentservice.entity.PaymentStatus;
import com.stripe.Stripe;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Real Stripe test-mode charges, active when {@code orderflow.payment.provider=stripe}
 * (env: {@code PAYMENT_PROVIDER=stripe}) with {@code STRIPE_API_KEY} set to a Stripe test secret
 * key ({@code sk_test_...}).
 *
 * <p><b>Unverified against a live Stripe account</b> - written against the documented
 * {@code stripe-java} 29.x PaymentIntent API (classic {@code Stripe.apiKey} static-key pattern,
 * not the newer {@code StripeClient} builder) but never exercised end-to-end since no test key
 * was available while building this. Smoke-test against your own Stripe test account before
 * relying on it.
 *
 * <p>{@code paymentToken} here is expected to be a real Stripe PaymentMethod id (starts with
 * {@code pm_}), not one of {@link MockPaymentGateway}'s made-up tokens (those have no meaning to
 * Stripe) - see <a href="https://docs.stripe.com/testing#payment-methods">Stripe's test
 * PaymentMethod catalog</a> for IDs that simulate declines etc. Blank defaults to
 * {@code pm_card_visa}, Stripe's documented always-succeeds test card. Passing one of our mock
 * tokens by mistake surfaces as a real Stripe "no such PaymentMethod" error (mapped to FAILED
 * below) rather than being silently reinterpreted.
 */
@Service
@ConditionalOnProperty(prefix = "orderflow.payment", name = "provider", havingValue = "stripe")
public class StripePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);
    private static final String DEFAULT_TEST_PAYMENT_METHOD = "pm_card_visa";

    public StripePaymentGateway(@Value("${orderflow.payment.stripe.api-key:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("orderflow.payment.provider=stripe but STRIPE_API_KEY is blank - every " +
                    "charge will fail with a Stripe authentication error until it's set.");
        }
        Stripe.apiKey = apiKey;
    }

    @Override
    public PaymentOutcome charge(long amountCents, String paymentToken) {
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountCents)
                    .setCurrency("usd")
                    .setPaymentMethod(resolvePaymentMethod(paymentToken))
                    .setConfirm(true)
                    // No customer present to handle a redirect/3DS challenge - keeps this
                    // synchronous for test-mode card charges instead of returning
                    // requires_action. A real checkout uses Stripe Elements/PaymentSheet
                    // client-side and wouldn't need this.
                    .setOffSession(true)
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);
            if ("succeeded".equals(intent.getStatus())) {
                return new PaymentOutcome(PaymentStatus.SUCCEEDED, intent.getId(), null);
            }
            return new PaymentOutcome(PaymentStatus.FAILED, intent.getId(),
                    "Stripe PaymentIntent did not complete synchronously, status=" + intent.getStatus());
        } catch (CardException e) {
            return new PaymentOutcome(PaymentStatus.FAILED, e.getRequestId(), e.getMessage());
        } catch (ApiConnectionException e) {
            return new PaymentOutcome(PaymentStatus.TIMEOUT, null, "Stripe connection error: " + e.getMessage());
        } catch (StripeException e) {
            log.error("Stripe charge failed", e);
            return new PaymentOutcome(PaymentStatus.FAILED, e.getRequestId(), e.getMessage());
        }
    }

    private String resolvePaymentMethod(String paymentToken) {
        return (paymentToken == null || paymentToken.isBlank()) ? DEFAULT_TEST_PAYMENT_METHOD : paymentToken;
    }
}
