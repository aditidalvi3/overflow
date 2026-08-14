# notification-service

Owns `notification_log` (`notification_db`). Purely event-driven, no JWT dependency. See the
[root README](../README.md) for the full saga sequence and [adr/](../adr/) for why.

## Saga participation

- `order.confirmed` → sends an "Order Confirmed" notification, logs it.
- `order.failed` → sends an "Order Failed" notification (includes the failure reason), logs it.

Optional debug endpoint: `GET /internal/notifications/{orderId}` (unauthenticated, not
gateway-routed).

## Notification provider

Two `NotificationSender` implementations, picked by config:

| Provider | `NOTIFICATION_PROVIDER` | What it does |
|---|---|---|
| Mock (default) | `mock` | Logs `[MOCK EMAIL] to=... subject=... body=...` at INFO. Always active unless `sendgrid` is explicitly set. |
| SendGrid | `sendgrid` | Real email via `sendgrid-java`'s `mail/send`. **Unverified against a live account** — see `SendGridNotificationSender`'s Javadoc. Needs `SENDGRID_API_KEY` and a `SENDGRID_FROM_ADDRESS` that's a verified sender/domain on your SendGrid account — an unverified from-address gets every send rejected. |

## Key env vars

| Var | Default | Purpose |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Saga event bus |
| `NOTIFICATION_PROVIDER` | `mock` | `mock` or `sendgrid` |
| `SENDGRID_API_KEY` | *(empty)* | Required only when `NOTIFICATION_PROVIDER=sendgrid` |
| `SENDGRID_FROM_ADDRESS` | `orders@orderflow.local` | Must be verified on your SendGrid account |

## Run its tests standalone

```bash
mvn -B test
```
