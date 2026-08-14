# payment-service

Owns `payments`/`pending_charges` (`payment_db`). Purely event-driven — no client-facing auth, no
JWT dependency, reachable only inside the docker network in this phase (a real deployment would
still want network policies/mTLS here; out of scope for this project). See the
[root README](../README.md) for the full saga sequence and [adr/](../adr/) for why.

## Saga participation

- `order.created` → caches `{amountCents, paymentToken}` for the order (it doesn't know these
  until it sees this event — `inventory.reserved` only carries `orderId`).
- `inventory.reserved` → the actual charge trigger: looks up the cached amount/token, calls the
  active `PaymentGateway`, persists a `Payment` row, publishes `payment.processed` or
  `payment.failed`.

Optional debug endpoint: `GET /internal/payments/{orderId}` (unauthenticated, not gateway-routed).

## Payment provider

Two `PaymentGateway` implementations, picked by config — swapping is an env var, not a code
change:

| Provider | `PAYMENT_PROVIDER` | What it does |
|---|---|---|
| Mock (default) | `mock` | Deterministic outcome by `paymentToken`: blank/unrecognized → succeeds, `tok_fail` → fails, `tok_timeout` → times out, `tok_random` → weighted random. Always active unless `stripe` is explicitly set. |
| Stripe | `stripe` | Real test-mode `PaymentIntent` charges via `stripe-java`. **Unverified against a live account** — see `StripePaymentGateway`'s Javadoc. Needs `STRIPE_API_KEY=sk_test_...`. `paymentToken` must be a real Stripe test PaymentMethod id (`pm_...`, blank defaults to `pm_card_visa`) — the mock tokens above have no meaning to Stripe and will surface as a real "no such PaymentMethod" error. |

## Key env vars

| Var | Default | Purpose |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Saga event bus |
| `PAYMENT_PROVIDER` | `mock` | `mock` or `stripe` |
| `STRIPE_API_KEY` | *(empty)* | Required only when `PAYMENT_PROVIDER=stripe` |

## Run its tests standalone

```bash
mvn -B test
```
