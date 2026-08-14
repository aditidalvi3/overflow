CREATE TABLE payments (
    id             BIGSERIAL PRIMARY KEY,
    order_id       BIGINT NOT NULL REFERENCES orders(id),
    amount_cents   BIGINT NOT NULL CHECK (amount_cents >= 0),
    status         VARCHAR(32) NOT NULL,
    provider_ref   VARCHAR(64),
    failure_reason VARCHAR(255),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_order_id ON payments(order_id);
