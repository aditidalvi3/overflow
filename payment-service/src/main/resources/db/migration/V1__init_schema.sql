CREATE TABLE payments (
    id             BIGSERIAL PRIMARY KEY,
    order_id       BIGINT NOT NULL,
    amount_cents   BIGINT NOT NULL CHECK (amount_cents >= 0),
    status         VARCHAR(32) NOT NULL,
    provider_ref   VARCHAR(64),
    failure_reason VARCHAR(255),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_order_id ON payments(order_id);

-- Local cache of order.created data (amount + payment token), keyed by orderId so a redelivery
-- of order.created is a plain idempotent upsert-by-primary-key. Filled by the order.created
-- consumer and read by the inventory.reserved consumer, which only carries {orderId}.
CREATE TABLE pending_charges (
    order_id      BIGINT PRIMARY KEY,
    amount_cents  BIGINT NOT NULL CHECK (amount_cents >= 0),
    payment_token VARCHAR(255),
    received_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE processed_events (
    id           BIGSERIAL PRIMARY KEY,
    topic        VARCHAR(64) NOT NULL,
    order_id     BIGINT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (topic, order_id)
);
