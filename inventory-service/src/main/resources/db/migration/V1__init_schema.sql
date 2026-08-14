CREATE TABLE products (
    id         BIGSERIAL PRIMARY KEY,
    sku        VARCHAR(64) NOT NULL UNIQUE,
    name       VARCHAR(255) NOT NULL,
    price_cents BIGINT NOT NULL CHECK (price_cents >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE inventory (
    product_id         BIGINT PRIMARY KEY REFERENCES products(id),
    quantity_available INTEGER NOT NULL CHECK (quantity_available >= 0),
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE inventory_reservations (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT NOT NULL,
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity   INTEGER NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_inventory_reservations_order_id ON inventory_reservations(order_id);

CREATE TABLE processed_events (
    id         BIGSERIAL PRIMARY KEY,
    topic      VARCHAR(64) NOT NULL,
    order_id   BIGINT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (topic, order_id)
);
