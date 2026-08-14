package com.orderflow.service;

import com.orderflow.entity.PaymentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayTest {

    private final MockPaymentGateway gateway = new MockPaymentGateway();

    @Test
    void charge_withNoToken_succeeds() {
        PaymentOutcome outcome = gateway.charge(1000L, null);

        assertThat(outcome.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(outcome.providerRef()).isNotBlank();
        assertThat(outcome.failureReason()).isNull();
    }

    @Test
    void charge_withBlankToken_succeeds() {
        assertThat(gateway.charge(1000L, "  ").status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void charge_withUnrecognizedToken_succeeds() {
        assertThat(gateway.charge(1000L, "tok_visa_4242").status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void charge_withFailToken_fails() {
        PaymentOutcome outcome = gateway.charge(1000L, "tok_fail");

        assertThat(outcome.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(outcome.failureReason()).isNotBlank();
    }

    @Test
    void charge_withFailToken_isCaseInsensitive() {
        assertThat(gateway.charge(1000L, "TOK_FAIL").status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void charge_withTimeoutToken_timesOut() {
        PaymentOutcome outcome = gateway.charge(1000L, "tok_timeout");

        assertThat(outcome.status()).isEqualTo(PaymentStatus.TIMEOUT);
        assertThat(outcome.failureReason()).isNotBlank();
    }

    @Test
    void charge_withRandomToken_returnsSomeOutcome() {
        PaymentOutcome outcome = gateway.charge(1000L, "tok_random");

        assertThat(outcome.status()).isIn((Object[]) PaymentStatus.values());
    }
}
